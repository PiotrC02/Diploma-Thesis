package com.example.sterownik_rgbw // Deklaracja pakietu aplikacji

// Importy potrzebnych klas i bibliotek
import android.Manifest // Import klas związanych z uprawnieniami
import android.app.AlertDialog // Import klasy do tworzenia okien dialogowych
import android.bluetooth.* // Import wszystkich klas związanych z Bluetooth
import android.content.pm.PackageManager // Import klasy do zarządzania uprawnieniami pakietów
import android.graphics.* // Import wszystkich klas graficznych
import android.os.* // Import wszystkich klas systemu operacyjnego
import android.view.* // Import wszystkich klas związanych z widokami
import android.widget.* // Import wszystkich klas widżetów
import androidx.appcompat.app.AppCompatActivity // Import klasy bazowej dla aktywności z ActionBar
import androidx.core.app.ActivityCompat // Import klasy do obsługi uprawnień
import androidx.core.content.ContextCompat // Import klasy do dostępu do zasobów kontekstu
import java.io.IOException // Import klasy do obsługi wyjątków wejścia/wyjścia
import java.io.OutputStream // Import klasy strumienia wyjściowego
import java.util.* // Import wszystkich klas narzędziowych
import kotlin.math.abs // Import funkcji matematycznej wartości bezwzględnej

// Główna klasa aktywności
class MainActivity : AppCompatActivity() {

    // Deklaracje elementów interfejsu użytkownika
    private lateinit var btnConnect: Button // Przycisk "Połącz"
    private lateinit var btnDisconnect: Button // Przycisk "Rozłącz"
    private lateinit var textViewButtonState: TextView // Tekst wyświetlający stan przycisku
    private lateinit var spinnerColor: Spinner // Spinner do wyboru koloru
    private lateinit var colorWheel: ImageView // Obraz koła kolorów
    private lateinit var colorIndicator: ImageView // Wskaźnik wybranego koloru na kole
    private lateinit var seekBarBrightness: SeekBar // Suwak do regulacji jasności
    private lateinit var brightnessPercentage: TextView // Tekst wyświetlający procent jasności

    // Zmienne do Bluetooth
    private var deviceAddress: String? = null // Zmienna przechowująca adres MAC urządzenia Bluetooth
    private lateinit var bluetoothAdapter: BluetoothAdapter // Adapter Bluetooth
    private var bluetoothSocket: BluetoothSocket? = null // Gniazdo Bluetooth do komunikacji
    private var outputStream: OutputStream? = null // Strumień wyjściowy do wysyłania danych
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID usługi Bluetooth SPP (Serial Port Profile)
    private val REQUEST_CODE = 1 // Kod żądania uprawnień

    // Pozostałe zmienne
    private var lastSentTime: Long = 0 // Czas ostatniego wysłania danych
    private val debounceTime: Long = 100 // Czas opóźnienia (debounce) między wysyłaniem danych
    private var ledState: Boolean = true // Stan diod LED (włączone/wyłączone)
    private var selectedColor: Int? = null // Wybrany kolor
    private lateinit var colorWheelBitmap: Bitmap // Bitmapa koła kolorów
    private val colors = listOf( // Lista dostępnych kolorów
        "Wybierz kolor",
        "Czerwony",
        "Zielony",
        "Niebieski",
        "Biały",
        "Żółty",
        "Cyjan",
        "Magenta"
    )

    private val handler = Handler(Looper.getMainLooper()) // Handler dla wątków głównego Looper'a
    private var isConnecting = false // Flaga informująca o tym, czy trwa łączenie
    private var isDisconnecting = false // Flaga informująca o tym, czy trwa rozłączanie

    // Przyciski efektów
    private lateinit var btnSmoothEffect: Button // Przycisk efektu "Płynny"
    private lateinit var btnBreathingEffect: Button // Przycisk efektu "Oddychanie"
    private lateinit var btnStrobeEffect: Button // Przycisk efektu "Stroboskop"
    private lateinit var btnStopEffect: Button // Przycisk "Zatrzymaj efekt"

    // Zmienne do zarządzania efektami
    private var effectHandler: Handler? = null // Handler dla efektów
    private var effectRunnable: Runnable? = null // Runnable dla efektów
    private var isEffectRunning = false // Flaga informująca, czy efekt jest uruchomiony
    private var currentEffect = "" // Nazwa aktualnie uruchomionego efektu

    // Metoda onCreate - punkt wejścia aktywności
    override fun onCreate(savedInstanceState: Bundle?) { // Metoda wywoływana przy tworzeniu aktywności
        super.onCreate(savedInstanceState) // Wywołanie metody nadrzędnej
        setContentView(R.layout.activity_main) // Ustawienie układu interfejsu użytkownika z pliku XML

        initializeUIComponents() // Inicjalizacja komponentów interfejsu użytkownika
        initializeBluetoothAdapter() // Inicjalizacja adaptera Bluetooth
        setupInitialUIState() // Ustawienie początkowego stanu interfejsu użytkownika
        setupEventListeners() // Ustawienie nasłuchiwaczy zdarzeń dla elementów interfejsu
        setupColorSpinner() // Konfiguracja spinnera z kolorami
        colorWheelBitmap = BitmapFactory.decodeResource(resources, R.drawable.rgb_color_wheel) // Załadowanie bitmapy koła kolorów
        checkAndRequestPermissions() // Sprawdzenie i żądanie wymaganych uprawnień
    }

    // Funkcja inicjalizująca komponenty interfejsu użytkownika
    private fun initializeUIComponents() {
        btnConnect = findViewById(R.id.btnConnect) // Pobranie referencji do przycisku "Połącz"
        btnDisconnect = findViewById(R.id.btnDisconnect) // Pobranie referencji do przycisku "Rozłącz"
        textViewButtonState = findViewById(R.id.textViewButtonState) // Pobranie referencji do tekstu stanu przycisku
        textViewButtonState.text = "Oczekiwanie" // Ustawienie początkowego tekstu stanu
        spinnerColor = findViewById(R.id.spinnerColor) // Pobranie referencji do spinnera kolorów
        colorWheel = findViewById(R.id.colorWheel) // Pobranie referencji do obrazu koła kolorów
        colorIndicator = findViewById(R.id.colorIndicator) // Pobranie referencji do wskaźnika koloru
        seekBarBrightness = findViewById(R.id.seekBarBrightness) // Pobranie referencji do suwaka jasności
        brightnessPercentage = findViewById(R.id.brightnessPercentage) // Pobranie referencji do tekstu procentu jasności
        brightnessPercentage.visibility = View.GONE // Ukrycie tekstu jasności na początku

        // Pobranie referencji do przycisków efektów
        btnSmoothEffect = findViewById(R.id.btnSmoothEffect) // Przycisk efektu "Płynny"
        btnBreathingEffect = findViewById(R.id.btnBreathingEffect) // Przycisk efektu "Oddychanie"
        btnStrobeEffect = findViewById(R.id.btnStrobeEffect) // Przycisk efektu "Stroboskop"
        btnStopEffect = findViewById(R.id.btnStopEffect) // Przycisk "Zatrzymaj efekt"
    }

    // Funkcja inicjalizująca adapter Bluetooth
    private fun initializeBluetoothAdapter() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager // Pobranie managera Bluetooth
        bluetoothAdapter = bluetoothManager.adapter // Pobranie adaptera Bluetooth
    }

    // Funkcja ustawiająca początkowy stan interfejsu użytkownika
    private fun setupInitialUIState() {
        colorWheel.isEnabled = false // Wyłączenie interakcji z kołem kolorów
        colorIndicator.visibility = View.GONE // Ukrycie wskaźnika koloru
        seekBarBrightness.isEnabled = false // Wyłączenie suwaka jasności
        btnDisconnect.isEnabled = false // Wyłączenie przycisku "Rozłącz"
        btnConnect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu przycisku "Połącz" na biały
        btnDisconnect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu przycisku "Rozłącz" na szary

        // Wyłączenie przycisków efektów na początku
        btnSmoothEffect.isEnabled = false // Wyłączenie przycisku efektu "Płynny"
        btnBreathingEffect.isEnabled = false // Wyłączenie przycisku efektu "Oddychanie"
        btnStrobeEffect.isEnabled = false // Wyłączenie przycisku efektu "Stroboskop"
        btnStopEffect.isEnabled = false // Wyłączenie przycisku "Zatrzymaj efekt"
        btnSmoothEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
        btnBreathingEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
        btnStrobeEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
        btnStopEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
    }

    // Funkcja ustawiająca nasłuchiwacze zdarzeń dla elementów interfejsu
    private fun setupEventListeners() {
        btnConnect.setOnClickListener { // Ustawienie nasłuchiwacza kliknięcia dla przycisku "Połącz"
            if (!isConnecting) { // Sprawdzenie, czy nie trwa już łączenie
                showDeviceListDialog() // Wyświetlenie listy sparowanych urządzeń
            }
        }

        btnDisconnect.setOnClickListener { // Ustawienie nasłuchiwacza kliknięcia dla przycisku "Rozłącz"
            if (!isDisconnecting) { // Sprawdzenie, czy nie trwa już rozłączanie
                isDisconnecting = true // Ustawienie flagi rozłączania
                btnDisconnect.isEnabled = false // Wyłączenie przycisku "Rozłącz"
                disconnectBluetooth() // Rozłączenie Bluetooth
                handler.postDelayed({ // Opóźnienie przed ponownym włączeniem przycisków
                    isDisconnecting = false // Reset flagi rozłączania
                    btnDisconnect.isEnabled = false // Upewnienie się, że przycisk "Rozłącz" jest wyłączony
                    btnConnect.isEnabled = true // Włączenie przycisku "Połącz"
                    btnConnect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
                    btnDisconnect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
                }, 2000) // Opóźnienie 2 sekund
            }
        }

        spinnerColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { // Ustawienie nasłuchiwacza wyboru dla spinnera
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { // Gdy wybrano pozycję
                if (position == 0) { // Jeśli to pierwsza pozycja ("Wybierz kolor")
                    return // Nie robić nic
                }
                if (bluetoothSocket?.isConnected == true) { // Jeśli jest połączony Bluetooth
                    selectedColor = getColorFromName(colors[position]) // Pobranie koloru na podstawie nazwy
                    seekBarBrightness.isEnabled = true // Włączenie suwaka jasności
                    brightnessPercentage.visibility = View.VISIBLE // Pokazanie tekstu procentu jasności
                    brightnessPercentage.text = "Jasność: ${seekBarBrightness.progress}%" // Ustawienie tekstu jasności
                    stopCurrentEffect() // Zatrzymanie aktualnego efektu
                    sendColorBrightness(selectedColor!!, seekBarBrightness.progress) // Wysłanie koloru i jasności
                    updateSpinnerTextColor(selectedColor!!) // Aktualizacja koloru tekstu spinnera
                    colorWheel.post { // Opóźnione wykonanie
                        moveIndicatorToColor(selectedColor!!) // Przesunięcie wskaźnika na kole kolorów
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Połącz się z modułem Bluetooth, aby zmienić kolor.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { // Gdy nic nie jest wybrane
                // Nie robić nic
            }
        }

        colorWheel.setOnTouchListener { _, event -> // Ustawienie nasłuchiwacza dotyku dla koła kolorów
            handleColorWheelTouch(event) // Obsługa dotyku na kole kolorów
            true // Zwrócenie true, aby wskazać, że zdarzenie zostało obsłużone
        }

        colorWheel.setOnClickListener {
            // Pusty nasłuchiwacz kliknięcia, aby uniknąć błędów
        }

        seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { // Ustawienie nasłuchiwacza zmiany dla suwaka jasności
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { // Gdy wartość suwaka się zmienia
                brightnessPercentage.visibility = View.VISIBLE // Pokazanie tekstu procentu jasności
                brightnessPercentage.text = "Jasność: $progress%" // Aktualizacja tekstu jasności
                if (bluetoothSocket?.isConnected == true && System.currentTimeMillis() - lastSentTime > debounceTime) { // Jeśli połączony i minął czas debounce
                    lastSentTime = System.currentTimeMillis() // Aktualizacja czasu ostatniego wysłania
                    selectedColor?.let { // Jeśli wybrano kolor
                        stopCurrentEffect() // Zatrzymanie aktualnego efektu
                        sendColorBrightness(it, progress) // Wysłanie koloru i jasności
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Nie robić nic
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) { // Gdy przestano przesuwać suwak
                if (bluetoothSocket?.isConnected == true) { // Jeśli połączony Bluetooth
                    val finalProgress = seekBar?.progress ?: 0 // Pobranie ostatecznej wartości
                    selectedColor?.let { // Jeśli wybrano kolor
                        stopCurrentEffect() // Zatrzymanie aktualnego efektu
                        sendColorBrightness(it, finalProgress) // Wysłanie koloru i jasności
                    }
                }
            }
        })

        // Ustawienie nasłuchiwaczy kliknięcia dla przycisków efektów
        btnSmoothEffect.setOnClickListener {
            if (bluetoothSocket?.isConnected == true) { // Jeśli połączony Bluetooth
                startSmoothEffect() // Uruchomienie efektu "Płynny"
            } else {
                Toast.makeText(this, "Połącz się z modułem Bluetooth, aby uruchomić efekt.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
            }
        }

        btnBreathingEffect.setOnClickListener {
            if (bluetoothSocket?.isConnected == true) { // Jeśli połączony Bluetooth
                if (selectedColor != null) { // Jeśli wybrano kolor
                    startBreathingEffect() // Uruchomienie efektu "Oddychanie"
                } else {
                    Toast.makeText(this, "Najpierw wybierz kolor, aby uruchomić efekt oddychania.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
                }
            } else {
                Toast.makeText(this, "Połącz się z modułem Bluetooth, aby uruchomić efekt.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
            }
        }

        btnStrobeEffect.setOnClickListener {
            if (bluetoothSocket?.isConnected == true) { // Jeśli połączony Bluetooth
                startStrobeEffect() // Uruchomienie efektu "Stroboskop"
            } else {
                Toast.makeText(this, "Połącz się z modułem Bluetooth, aby uruchomić efekt.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
            }
        }

        btnStopEffect.setOnClickListener {
            if (bluetoothSocket?.isConnected == true) { // Jeśli połączony Bluetooth
                stopCurrentEffect() // Zatrzymanie aktualnego efektu
            } else {
                Toast.makeText(this, "Połącz się z modułem Bluetooth, aby zatrzymać efekt.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
            }
        }
    }

    // Funkcja konfigurująca spinner z kolorami
    private fun setupColorSpinner() {
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, colors) { // Tworzenie adaptera dla spinnera
            override fun isEnabled(position: Int): Boolean { // Nadpisanie metody sprawdzającej, czy pozycja jest włączona
                return position != 0 // Wyłączenie pierwszej pozycji ("Wybierz kolor")
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View { // Nadpisanie metody pobierającej widok rozwijanego menu
                val view = super.getDropDownView(position, convertView, parent) // Pobranie widoku dla pozycji
                val tv = view as TextView // Rzutowanie widoku na TextView
                tv.setTextColor(if (position == 0) Color.GRAY else Color.BLACK) // Ustawienie koloru tekstu pozycji
                return view // Zwrócenie widoku
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) // Ustawienie układu rozwijanego menu
        spinnerColor.adapter = adapter // Przypisanie adaptera do spinnera
        spinnerColor.isEnabled = false // Wyłączenie spinnera na początku
    }

    // Metoda wysyłająca kolor i jasność do Arduino
    private fun sendColorBrightness(color: Int, brightness: Int) {
        val red: Int // Zmienna na składową czerwoną
        val green: Int // Zmienna na składową zieloną
        val blue: Int // Zmienna na składową niebieską
        val white: Int // Zmienna na składową białą

        if (isColorWhite(color)) { // Jeśli kolor jest biały
            red = 0 // Ustawienie składowej czerwonej na 0
            green = 0 // Ustawienie składowej zielonej na 0
            blue = 0 // Ustawienie składowej niebieskiej na 0
            white = 255 * brightness / 100 // Ustawienie jasności białego
        } else {
            red = Color.red(color) * brightness / 100 // Skalowanie składowej czerwonej
            green = Color.green(color) * brightness / 100 // Skalowanie składowej zielonej
            blue = Color.blue(color) * brightness / 100 // Skalowanie składowej niebieskiej
            white = 0 // Ustawienie składowej białej na 0
        }

        val command = "$red $green $blue $white\n" // Przygotowanie komendy do wysłania
        try {
            outputStream?.write(command.toByteArray()) // Wysłanie komendy przez strumień wyjściowy
            updateButtonState(isConnected = true) // Aktualizacja stanu przycisków
        } catch (_: IOException) {
            // Obsługa wyjątków
        }
    }

    // Funkcja pobierająca kolor na podstawie nazwy
    private fun getColorFromName(colorName: String): Int {
        return when (colorName) { // Sprawdzenie nazwy koloru
            "Czerwony" -> Color.RED // Zwrócenie koloru czerwonego
            "Zielony" -> Color.GREEN // Zwrócenie koloru zielonego
            "Niebieski" -> Color.BLUE // Zwrócenie koloru niebieskiego
            "Biały" -> Color.WHITE // Zwrócenie koloru białego
            "Żółty" -> Color.YELLOW // Zwrócenie koloru żółtego
            "Cyjan" -> Color.CYAN // Zwrócenie koloru cyjan
            "Magenta" -> Color.MAGENTA // Zwrócenie koloru magenta
            else -> Color.BLACK // W przeciwnym razie zwrócenie koloru czarnego
        }
    }

    // Funkcja pomocnicza do sprawdzenia, czy kolor jest biały
    private fun isColorWhite(color: Int): Boolean {
        val red = Color.red(color) // Pobranie składowej czerwonej
        val green = Color.green(color) // Pobranie składowej zielonej
        val blue = Color.blue(color) // Pobranie składowej niebieskiej
        val threshold = 240 // Próg dla białego
        return red >= threshold && green >= threshold && blue >= threshold // Sprawdzenie, czy kolor jest biały
    }

    // Funkcja obsługująca dotyk na kole kolorów
    private fun handleColorWheelTouch(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) { // Jeśli dotyk jest naciśnięciem lub przesunięciem
            val x = event.x.toInt() // Pobranie pozycji x dotyku
            val y = event.y.toInt() // Pobranie pozycji y dotyku
            val imageViewWidth = colorWheel.width // Szerokość ImageView
            val imageViewHeight = colorWheel.height // Wysokość ImageView
            val bitmapX = x * colorWheelBitmap.width / imageViewWidth // Przeliczenie na współrzędne bitmapy
            val bitmapY = y * colorWheelBitmap.height / imageViewHeight // Przeliczenie na współrzędne bitmapy
            if (bitmapX in 0 until colorWheelBitmap.width && bitmapY in 0 until colorWheelBitmap.height) { // Sprawdzenie, czy współrzędne są w bitmapie
                val pixel = colorWheelBitmap.getPixel(bitmapX, bitmapY) // Pobranie koloru piksela
                if (Color.alpha(pixel) != 0) { // Jeśli piksel nie jest przezroczysty
                    selectedColor = pixel // Ustawienie wybranego koloru
                    seekBarBrightness.isEnabled = true // Włączenie suwaka jasności
                    brightnessPercentage.visibility = View.VISIBLE // Pokazanie tekstu procentu jasności
                    brightnessPercentage.text = "Jasność: ${seekBarBrightness.progress}%" // Aktualizacja tekstu jasności
                    colorIndicator.x = colorWheel.x + event.x - colorIndicator.width / 2 // Ustawienie pozycji wskaźnika x
                    colorIndicator.y = colorWheel.y + event.y - colorIndicator.height / 2 // Ustawienie pozycji wskaźnika y
                    colorIndicator.visibility = View.VISIBLE // Pokazanie wskaźnika koloru
                    if (bluetoothSocket?.isConnected == true && System.currentTimeMillis() - lastSentTime > debounceTime) { // Jeśli połączony i minął czas debounce
                        lastSentTime = System.currentTimeMillis() // Aktualizacja czasu ostatniego wysłania
                        stopCurrentEffect() // Zatrzymanie aktualnego efektu
                        sendColorBrightness(selectedColor!!, seekBarBrightness.progress) // Wysłanie koloru i jasności
                    }
                    resetSpinnerTextColor() // Reset koloru tekstu spinnera
                    spinnerColor.setSelection(0) // Reset wyboru spinnera
                }
            }
        }
        if (event.action == MotionEvent.ACTION_UP) { // Jeśli dotyk został zwolniony
            colorWheel.performClick() // Wywołanie kliknięcia dla zgodności
        }
    }

    // Funkcja przesuwająca wskaźnik do wybranego koloru na kole barw
    private fun moveIndicatorToColor(color: Int) {
        if (colorWheel.width == 0 || colorWheel.height == 0) { // Jeśli rozmiar ImageView nie jest jeszcze ustalony
            val listener = object : ViewTreeObserver.OnGlobalLayoutListener { // Tworzenie nasłuchiwacza układu
                override fun onGlobalLayout() {
                    colorWheel.viewTreeObserver.removeOnGlobalLayoutListener(this) // Usunięcie nasłuchiwacza
                    moveIndicatorToColor(color) // Ponowne wywołanie funkcji
                }
            }
            colorWheel.viewTreeObserver.addOnGlobalLayoutListener(listener) // Dodanie nasłuchiwacza
            return // Wyjście z funkcji
        }
        val bitmapWidth = colorWheelBitmap.width // Szerokość bitmapy
        val bitmapHeight = colorWheelBitmap.height // Wysokość bitmapy
        var foundX = -1 // Zmienna na znalezioną pozycję x
        var foundY = -1 // Zmienna na znalezioną pozycję y
        val step = 5 // Krok przeszukiwania
        outer@ for (x in 0 until bitmapWidth step step) { // Pętla po x z krokiem
            for (y in 0 until bitmapHeight step step) { // Pętla po y z krokiem
                val pixel = colorWheelBitmap.getPixel(x, y) // Pobranie koloru piksela
                if (Color.alpha(pixel) != 0 && colorsAreSimilar(pixel, color)) { // Jeśli piksel nie jest przezroczysty i kolory są podobne
                    foundX = x // Ustawienie znalezionej pozycji x
                    foundY = y // Ustawienie znalezionej pozycji y
                    break@outer // Wyjście z pętli
                }
            }
        }
        if (foundX != -1 && foundY != -1) { // Jeśli znaleziono pozycję
            val imageViewWidth = colorWheel.width.toFloat() // Szerokość ImageView
            val imageViewHeight = colorWheel.height.toFloat() // Wysokość ImageView
            val posX = foundX * imageViewWidth / bitmapWidth // Przeliczenie na współrzędne ImageView
            val posY = foundY * imageViewHeight / bitmapHeight // Przeliczenie na współrzędne ImageView
            runOnUiThread { // Wykonanie na wątku głównym
                colorIndicator.x = colorWheel.x + posX - colorIndicator.width / 2 // Ustawienie pozycji wskaźnika x
                colorIndicator.y = colorWheel.y + posY - colorIndicator.height / 2 // Ustawienie pozycji wskaźnika y
                colorIndicator.visibility = View.VISIBLE // Pokazanie wskaźnika koloru
            }
        } else {
            runOnUiThread { // Wykonanie na wątku głównym
                colorIndicator.visibility = View.GONE // Ukrycie wskaźnika koloru
            }
        }
    }

    // Funkcja sprawdzająca, czy dwa kolory są podobne
    private fun colorsAreSimilar(color1: Int, color2: Int): Boolean {
        val threshold = 30 // Próg podobieństwa
        val redDiff = abs(Color.red(color1) - Color.red(color2)) // Różnica składowej czerwonej
        val greenDiff = abs(Color.green(color1) - Color.green(color2)) // Różnica składowej zielonej
        val blueDiff = abs(Color.blue(color1) - Color.blue(color2)) // Różnica składowej niebieskiej
        return redDiff < threshold && greenDiff < threshold && blueDiff < threshold // Zwrócenie true, jeśli różnice są mniejsze niż próg
    }

    // Funkcja aktualizująca kolor tekstu w spinnerze
    private fun updateSpinnerTextColor(color: Int) {
        val selectedView = spinnerColor.selectedView as? TextView // Pobranie aktualnie wybranego widoku spinnera
        selectedView?.setTextColor(color) // Ustawienie koloru tekstu
    }

    // Funkcja resetująca kolor tekstu w spinnerze
    private fun resetSpinnerTextColor() {
        val selectedView = spinnerColor.selectedView as? TextView // Pobranie aktualnie wybranego widoku spinnera
        selectedView?.setTextColor(Color.BLACK) // Reset koloru tekstu do czarnego
    }

    // Metoda do nawiązania połączenia Bluetooth z wybranym urządzeniem
    private fun connectBluetooth(device: BluetoothDevice) {
        if (!bluetoothAdapter.isEnabled) { // Jeśli Bluetooth jest wyłączony
            Toast.makeText(this, "Bluetooth jest wyłączony. Proszę włączyć Bluetooth.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
            isConnecting = false // Reset flagi łączenia
            btnConnect.isEnabled = true // Włączenie przycisku "Połącz"
            return // Wyjście z funkcji
        }

        val hasBluetoothConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Sprawdzenie wersji Androida
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED // Sprawdzenie uprawnienia
        } else {
            true // Dla starszych wersji uprawnienie nie jest wymagane
        }

        if (!hasBluetoothConnectPermission) { // Jeśli brak uprawnienia BLUETOOTH_CONNECT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Sprawdzenie wersji Androida
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_CODE) // Żądanie uprawnienia
            }
            Toast.makeText(this, "Brak uprawnień BLUETOOTH_CONNECT.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
            isConnecting = false // Reset flagi łączenia
            btnConnect.isEnabled = true // Włączenie przycisku "Połącz"
            return // Wyjście z funkcji
        }

        isConnecting = true // Ustawienie flagi łączenia
        btnConnect.isEnabled = false // Wyłączenie przycisku "Połącz"

        Thread { // Uruchomienie nowego wątku
            try {
                deviceAddress = device.address // Pobranie adresu MAC wybranego urządzenia
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid) // Utworzenie gniazda Bluetooth
                bluetoothAdapter.cancelDiscovery() // Anulowanie wyszukiwania urządzeń
                bluetoothSocket?.connect() // Połączenie z urządzeniem
                outputStream = bluetoothSocket?.outputStream // Pobranie strumienia wyjściowego
                runOnUiThread {
                    val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Sprawdzenie wersji Androida
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) { // Sprawdzenie uprawnienia
                            device.name ?: "Urządzenie Bluetooth" // Pobranie nazwy urządzenia
                        } else {
                            "Urządzenie Bluetooth" // Jeśli brak uprawnień
                        }
                    } else {
                        device.name ?: "Urządzenie Bluetooth" // Dla starszych wersji
                    }
                    Toast.makeText(this, "Połączono z urządzeniem $deviceName.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
                    outputStream?.write("CONNECTED\n".toByteArray()) // Wysłanie informacji o połączeniu
                    updateButtonState(isConnected = true) // Aktualizacja stanu przycisków
                    listenForData() // Rozpoczęcie nasłuchiwania danych
                    isConnecting = false // Reset flagi łączenia
                    btnConnect.isEnabled = false // Wyłączenie przycisku "Połącz"
                    btnDisconnect.isEnabled = true // Włączenie przycisku "Rozłącz"
                    btnConnect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
                    btnDisconnect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
                }
            } catch (e: IOException) { // Obsługa wyjątków
                runOnUiThread {
                    Toast.makeText(this, "Nieudane połączenie z urządzeniem.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
                    try {
                        bluetoothSocket?.close() // Próba zamknięcia gniazda
                    } catch (ex: IOException) {
                        ex.printStackTrace() // Wypisanie stosu błędów
                    }
                    isConnecting = false // Reset flagi łączenia
                    btnConnect.isEnabled = true // Włączenie przycisku "Połącz"
                    btnConnect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
                    btnDisconnect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
                }
            }
        }.start() // Start wątku
    }

    // Obsługa wyniku żądania uprawnień
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE) { // Sprawdzenie kodu żądania
            for (i in permissions.indices) { // Iteracja po uprawnieniach
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) { // Jeśli uprawnienie nie zostało przyznane
                    Toast.makeText(this, "Brak wymaganych uprawnień.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) // Wywołanie metody nadrzędnej
    }

    // Funkcja sprawdzająca i żądająca uprawnień
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>() // Lista uprawnień do żądania
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) { // Sprawdzenie uprawnienia BLUETOOTH
            permissions.add(Manifest.permission.BLUETOOTH) // Dodanie uprawnienia Bluetooth
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) { // Sprawdzenie uprawnienia BLUETOOTH_ADMIN
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN) // Dodanie uprawnienia administratora Bluetooth
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Sprawdzenie wersji Androida
            // Sprawdzanie uprawnień dla Androida 12 i nowszych
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { // Sprawdzenie uprawnienia BLUETOOTH_CONNECT
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT) // Dodanie uprawnienia do łączenia Bluetooth
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) { // Sprawdzenie uprawnienia BLUETOOTH_SCAN
                permissions.add(Manifest.permission.BLUETOOTH_SCAN) // Dodanie uprawnienia do skanowania Bluetooth
            }
        }
        if (permissions.isNotEmpty()) { // Jeśli lista uprawnień nie jest pusta
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CODE) // Żądanie uprawnień
        }
    }

    // Metoda wyświetlająca listę sparowanych urządzeń Bluetooth
    private fun showDeviceListDialog() {
        // Sprawdzenie, czy Bluetooth jest włączony
        if (!bluetoothAdapter.isEnabled) { // Jeśli Bluetooth jest wyłączony
            Toast.makeText(this, "Bluetooth jest wyłączony. Proszę włączyć Bluetooth.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
            isConnecting = false // Reset flagi łączenia
            btnConnect.isEnabled = true // Włączenie przycisku "Połącz"
            return // Wyjście z funkcji
        }

        val hasBluetoothConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Sprawdzenie wersji Androida
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED // Sprawdzenie uprawnienia
        } else {
            true // Dla starszych wersji uprawnienie nie jest wymagane
        }

        if (!hasBluetoothConnectPermission) { // Jeśli brak uprawnienia BLUETOOTH_CONNECT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Sprawdzenie wersji Androida
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_CODE) // Żądanie uprawnienia
            }
            Toast.makeText(this, "Brak uprawnień BLUETOOTH_CONNECT.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
            isConnecting = false // Reset flagi łączenia
            btnConnect.isEnabled = true // Włączenie przycisku "Połącz"
            return // Wyjście z funkcji
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices // Pobranie sparowanych urządzeń
        if (pairedDevices == null || pairedDevices.isEmpty()) { // Jeśli brak sparowanych urządzeń
            Toast.makeText(this, "Brak sparowanych urządzeń Bluetooth.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
            isConnecting = false // Reset flagi łączenia
            btnConnect.isEnabled = true // Włączenie przycisku "Połącz"
            return // Wyjście z funkcji
        }

        val deviceList = pairedDevices.toList() // Konwersja na listę
        val deviceNames = deviceList.map { device -> // Przygotowanie listy nazw urządzeń
            val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Sprawdzenie wersji Androida
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) { // Sprawdzenie uprawnienia
                    device.name // Pobranie nazwy urządzenia
                } else {
                    "Nieznane urządzenie" // Jeśli brak uprawnień
                }
            } else {
                device.name ?: "Nieznane urządzenie" // Dla starszych wersji
            }
            if (deviceName != null) "$deviceName\n${device.address}" else device.address // Formatowanie nazwy
        }

        val builder = AlertDialog.Builder(this) // Tworzenie dialogu
        builder.setTitle("Wybierz urządzenie") // Ustawienie tytułu
        builder.setItems(deviceNames.toTypedArray()) { _, which -> // Ustawienie elementów dialogu
            val device = deviceList[which] // Pobranie wybranego urządzenia
            connectBluetooth(device) // Nawiązanie połączenia
        }
        builder.setNegativeButton("Anuluj") { dialog, _ -> // Ustawienie przycisku "Anuluj"
            dialog.dismiss() // Zamknięcie dialogu
            isConnecting = false // Reset flagi łączenia
            btnConnect.isEnabled = true // Włączenie przycisku "Połącz"
        }
        runOnUiThread {
            builder.show() // Wyświetlenie dialogu
        }
    }

    // Funkcja nasłuchująca dane z Bluetooth
    private fun listenForData() {
        Thread { // Uruchomienie nowego wątku
            try {
                val inputStream = bluetoothSocket?.inputStream // Pobranie strumienia wejściowego
                val bufferedReader = inputStream?.bufferedReader() // Utworzenie BufferedReader
                while (bluetoothSocket?.isConnected == true) { // Pętla dopóki jest połączenie
                    val data = bufferedReader?.readLine() // Odczyt linii danych
                    if (data != null) { // Jeśli dane nie są puste
                        runOnUiThread {
                            if (data.contains("BUTTON_ON")) { // Jeśli otrzymano "BUTTON_ON"
                                ledState = true // Ustawienie stanu diod na włączone
                                textViewButtonState.text = "LED włączony" // Aktualizacja tekstu stanu
                                updateButtonState(isConnected = true) // Aktualizacja stanu przycisków
                                // Możesz opcjonalnie wznowić efekt tutaj
                            } else if (data.contains("BUTTON_OFF")) { // Jeśli otrzymano "BUTTON_OFF"
                                ledState = false // Ustawienie stanu diod na wyłączone
                                textViewButtonState.text = "LED wyłączony" // Aktualizacja tekstu stanu
                                stopCurrentEffect() // Zatrzymanie efektu
                                updateButtonState(isConnected = true) // Aktualizacja stanu przycisków
                            }
                        }
                    }
                    Thread.sleep(100) // Krótkie opóźnienie
                }
            } catch (_: IOException) {
                // Obsługa wyjątków
            }
        }.start() // Start wątku
    }

    // Funkcja rozłączająca Bluetooth
    private fun disconnectBluetooth() {
        Thread { // Uruchomienie nowego wątku
            try {
                outputStream?.write("DISCONNECTED\n".toByteArray()) // Wysłanie informacji o rozłączeniu
                bluetoothSocket?.close() // Zamknięcie gniazda Bluetooth
                outputStream?.close() // Zamknięcie strumienia wyjściowego
                runOnUiThread {
                    Toast.makeText(this, "Rozłączono z urządzeniem Bluetooth.", Toast.LENGTH_SHORT).show() // Wyświetlenie komunikatu
                    updateButtonState(isConnected = false) // Aktualizacja stanu przycisków
                    textViewButtonState.text = "Oczekiwanie" // Reset tekstu stanu
                    resetSpinnerTextColor() // Reset koloru tekstu spinnera
                    seekBarBrightness.isEnabled = false // Wyłączenie suwaka jasności
                    selectedColor = null // Reset wybranego koloru
                    spinnerColor.setSelection(0) // Reset pozycji spinnera
                    colorIndicator.visibility = View.GONE // Ukrycie wskaźnika koloru
                    brightnessPercentage.text = "" // Reset tekstu jasności
                    brightnessPercentage.visibility = View.GONE // Ukrycie procentu jasności
                    isDisconnecting = false // Reset flagi rozłączania
                    btnDisconnect.isEnabled = false // Wyłączenie przycisku "Rozłącz"
                    btnConnect.isEnabled = true // Włączenie przycisku "Połącz"
                    btnConnect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
                    btnDisconnect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
                    stopCurrentEffect() // Zatrzymanie efektu
                }
            } catch (_: IOException) { // Obsługa wyjątków
                runOnUiThread {
                    isDisconnecting = false // Reset flagi rozłączania
                    btnDisconnect.isEnabled = false // Wyłączenie przycisku "Rozłącz"
                    btnConnect.isEnabled = true // Włączenie przycisku "Połącz"
                    btnConnect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
                    btnDisconnect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
                }
            }
        }.start() // Start wątku
    }

    // Funkcja aktualizująca stany przycisków
    private fun updateButtonState(isConnected: Boolean) {
        btnConnect.isEnabled = !isConnected // Ustawienie stanu przycisku połączenia
        btnDisconnect.isEnabled = isConnected // Ustawienie stanu przycisku rozłączenia
        if (isConnected) {
            btnConnect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
            btnDisconnect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
        } else {
            btnConnect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
            btnDisconnect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
        }
        spinnerColor.isEnabled = isConnected // Ustawienie stanu spinnera
        colorWheel.isEnabled = isConnected // Ustawienie stanu koła kolorów
        textViewButtonState.text = when { // Aktualizacja tekstu stanu
            !isConnected -> "Oczekiwanie" // Jeśli nie jest połączony
            ledState -> "LED włączony" // Jeśli diody są włączone
            else -> "LED wyłączony" // Jeśli diody są wyłączone
        }
        colorIndicator.visibility = if (isConnected && ledState && selectedColor != null) View.VISIBLE else View.GONE // Ustawienie widoczności wskaźnika koloru

        // Aktualizacja stanu przycisków efektów
        if (isConnected) {
            if (!isEffectRunning) { // Jeśli efekt nie jest uruchomiony
                btnSmoothEffect.isEnabled = true // Włączenie przycisku efektu "Płynny"
                btnBreathingEffect.isEnabled = true // Włączenie przycisku efektu "Oddychanie"
                btnStrobeEffect.isEnabled = true // Włączenie przycisku efektu "Stroboskop"
                btnStopEffect.isEnabled = false // Wyłączenie przycisku "Zatrzymaj efekt"

                btnSmoothEffect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
                btnBreathingEffect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
                btnStrobeEffect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
                btnStopEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
            }
        } else {
            btnSmoothEffect.isEnabled = false // Wyłączenie przycisku efektu "Płynny"
            btnBreathingEffect.isEnabled = false // Wyłączenie przycisku efektu "Oddychanie"
            btnStrobeEffect.isEnabled = false // Wyłączenie przycisku efektu "Stroboskop"
            btnStopEffect.isEnabled = false // Wyłączenie przycisku "Zatrzymaj efekt"

            btnSmoothEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
            btnBreathingEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
            btnStrobeEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
            btnStopEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
        }

        if (!isConnected) { // Jeśli nie jest połączony
            resetSpinnerTextColor() // Reset koloru tekstu spinnera
            seekBarBrightness.isEnabled = false // Wyłączenie suwaka jasności
            selectedColor = null // Reset wybranego koloru
            spinnerColor.setSelection(0) // Reset wyboru spinnera
            colorIndicator.visibility = View.GONE // Ukrycie wskaźnika koloru
            brightnessPercentage.text = "" // Reset tekstu jasności
            brightnessPercentage.visibility = View.GONE // Ukrycie tekstu jasności
            stopCurrentEffect() // Zatrzymanie aktualnego efektu
        }
    }

    // Funkcja uruchamiająca efekt "Płynny"
    private fun startSmoothEffect() {
        stopCurrentEffect() // Zatrzymanie aktualnego efektu
        currentEffect = "SMOOTH" // Ustawienie nazwy efektu
        effectHandler = Handler(Looper.getMainLooper()) // Inicjalizacja handlera dla efektu
        var hue = 0 // Zmienna na odcień koloru
        effectRunnable = object : Runnable { // Utworzenie Runnable dla efektu
            override fun run() {
                if (!ledState) { // Jeśli diody są wyłączone
                    stopCurrentEffect() // Zatrzymanie efektu
                    return // Wyjście z funkcji
                }
                val hsv = floatArrayOf(hue.toFloat(), 1f, 1f) // Tablica HSV
                val color = Color.HSVToColor(hsv) // Konwersja HSV na kolor
                sendColorBrightness(color, 100) // Wysłanie koloru z pełną jasnością
                hue = (hue + 2) % 360 // Wolniejsze zmiany koloru
                effectHandler?.postDelayed(this, 150) // Zwiększenie opóźnienia do 150 ms
            }
        }
        effectHandler?.post(effectRunnable!!) // Uruchomienie Runnable
        isEffectRunning = true // Ustawienie flagi efektu na uruchomiony
        updateEffectButtons() // Aktualizacja stanu przycisków efektów
    }

    // Funkcja uruchamiająca efekt "Oddychanie"
    private fun startBreathingEffect() {
        stopCurrentEffect() // Zatrzymanie aktualnego efektu
        currentEffect = "BREATHING" // Ustawienie nazwy efektu
        effectHandler = Handler(Looper.getMainLooper()) // Inicjalizacja handlera dla efektu
        var brightness = 0 // Zmienna na jasność
        var increasing = true // Flaga określająca kierunek zmiany jasności
        effectRunnable = object : Runnable { // Utworzenie Runnable dla efektu
            override fun run() {
                if (!ledState) { // Jeśli diody są wyłączone
                    stopCurrentEffect() // Zatrzymanie efektu
                    return // Wyjście z funkcji
                }
                sendColorBrightness(selectedColor!!, brightness) // Wysłanie koloru z aktualną jasnością
                if (increasing) { // Jeśli jasność rośnie
                    brightness += 5 // Wolniejsze zmiany jasności
                    if (brightness >= 100) { // Jeśli osiągnięto maksymalną jasność
                        brightness = 100 // Ustawienie na maksymalną jasność
                        increasing = false // Zmiana kierunku
                    }
                } else { // Jeśli jasność maleje
                    brightness -= 5 // Zmniejszenie jasności
                    if (brightness <= 0) { // Jeśli osiągnięto minimalną jasność
                        brightness = 0 // Ustawienie na minimalną jasność
                        increasing = true // Zmiana kierunku
                    }
                }
                effectHandler?.postDelayed(this, 150) // Zwiększenie opóźnienia do 150 ms
            }
        }
        effectHandler?.post(effectRunnable!!) // Uruchomienie Runnable
        isEffectRunning = true // Ustawienie flagi efektu na uruchomiony
        updateEffectButtons() // Aktualizacja stanu przycisków efektów
    }

    // Funkcja uruchamiająca efekt "Stroboskop"
    private fun startStrobeEffect() {
        stopCurrentEffect() // Zatrzymanie aktualnego efektu
        currentEffect = "STROBE" // Ustawienie nazwy efektu
        effectHandler = Handler(Looper.getMainLooper()) // Inicjalizacja handlera dla efektu
        effectRunnable = object : Runnable { // Utworzenie Runnable dla efektu
            override fun run() {
                if (!ledState) { // Jeśli diody są wyłączone
                    stopCurrentEffect() // Zatrzymanie efektu
                    return // Wyjście z funkcji
                }
                val useWhiteChannel = Random().nextBoolean() // Losowe użycie kanału białego
                val randomColor: Int = if (useWhiteChannel) {
                    Color.WHITE // Użycie kanału białego
                } else {
                    Color.rgb(Random().nextInt(256), Random().nextInt(256), Random().nextInt(256)) // Losowy kolor RGB
                }
                val randomBrightness = Random().nextInt(101) // Losowa jasność od 0 do 100
                sendColorBrightness(randomColor, randomBrightness) // Wysłanie koloru i jasności
                effectHandler?.postDelayed(this, 200) // Zwiększenie opóźnienia do 200 ms
            }
        }
        effectHandler?.post(effectRunnable!!) // Uruchomienie Runnable
        isEffectRunning = true // Ustawienie flagi efektu na uruchomiony
        updateEffectButtons() // Aktualizacja stanu przycisków efektów
    }

    // Funkcja zatrzymująca bieżący efekt
    private fun stopCurrentEffect() {
        if (isEffectRunning) { // Jeśli efekt jest uruchomiony
            effectHandler?.removeCallbacks(effectRunnable!!) // Usunięcie callbacków
            isEffectRunning = false // Ustawienie flagi efektu na zatrzymany
            currentEffect = "" // Reset nazwy efektu
            updateEffectButtons() // Aktualizacja stanu przycisków efektów
        }
    }

    // Metoda aktualizująca stany przycisków efektów
    private fun updateEffectButtons() {
        if (isEffectRunning) { // Jeśli efekt jest uruchomiony
            // Dezaktywacja przycisków efektów, aktywacja przycisku zatrzymania
            btnSmoothEffect.isEnabled = false // Wyłączenie przycisku efektu "Płynny"
            btnBreathingEffect.isEnabled = false // Wyłączenie przycisku efektu "Oddychanie"
            btnStrobeEffect.isEnabled = false // Wyłączenie przycisku efektu "Stroboskop"
            btnStopEffect.isEnabled = true // Włączenie przycisku "Zatrzymaj efekt"

            btnSmoothEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
            btnBreathingEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
            btnStrobeEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
            btnStopEffect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
        } else {
            // Aktywacja przycisków efektów, dezaktywacja przycisku zatrzymania
            btnSmoothEffect.isEnabled = true // Włączenie przycisku efektu "Płynny"
            btnBreathingEffect.isEnabled = true // Włączenie przycisku efektu "Oddychanie"
            btnStrobeEffect.isEnabled = true // Włączenie przycisku efektu "Stroboskop"
            btnStopEffect.isEnabled = false // Wyłączenie przycisku "Zatrzymaj efekt"

            btnSmoothEffect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
            btnBreathingEffect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
            btnStrobeEffect.setTextColor(Color.WHITE) // Ustawienie koloru tekstu na biały
            btnStopEffect.setTextColor(Color.GRAY) // Ustawienie koloru tekstu na szary
        }
    }
}
