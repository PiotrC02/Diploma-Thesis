#include <Arduino.h> // Dołączenie biblioteki Arduino

const int redPin = 11;   // Numer pinu dla czerwonej diody LED
const int greenPin = 10; // Numer pinu dla zielonej diody LED
const int bluePin = 9;   // Numer pinu dla niebieskiej diody LED
const int whitePin = 6;  // Numer pinu dla białej diody LED
const int switchPin = 3; // Numer pinu dla przycisku

bool ledsOn = false; // Zmienna przechowująca stan diod LED (włączone/wyłączone)
int lastRed = 0, lastGreen = 0, lastBlue = 0, lastWhite = 0; // Ostatnie ustawienia kolorów
int lastBrightness = 100; // Ostatnia ustawiona jasność

bool lastButtonState = HIGH; // Poprzedni stan przycisku
bool isBluetoothConnected = false; // Czy Bluetooth jest połączony
bool isBluetoothOverrideActive = false; // Czy Bluetooth nadpisuje ustawienia
unsigned long lastChangeTime = 0; // Czas ostatniej zmiany koloru

unsigned long buttonPressTime = 0; // Czas naciśnięcia przycisku
const unsigned long debounceTime = 1000; // Czas eliminacji drgań styków (debounce)

void setup() {
  pinMode(redPin, OUTPUT);   // Ustawienie pinu czerwonej diody jako wyjście
  pinMode(greenPin, OUTPUT); // Ustawienie pinu zielonej diody jako wyjście
  pinMode(bluePin, OUTPUT);  // Ustawienie pinu niebieskiej diody jako wyjście
  pinMode(whitePin, OUTPUT); // Ustawienie pinu białej diody jako wyjście
  pinMode(switchPin, INPUT_PULLUP); // Ustawienie pinu przycisku jako wejście z podciąganiem

  setColor(0, 0, 0, 0); // Wyłączenie wszystkich diod LED

  Serial.begin(9600); // Inicjalizacja komunikacji szeregowej
  Serial.println("Setup complete"); // Informacja o zakończeniu konfiguracji Bluetooth
  Serial.println(ledsOn ? "BUTTON_ON" : "BUTTON_OFF"); // Status LED-ów i przycisku
}

void loop() {
  bool currentButtonState = digitalRead(switchPin); // Odczytanie stanu przycisku

  if (currentButtonState == LOW) { // Jeśli przycisk jest wciśnięty
    if (buttonPressTime == 0) {
      buttonPressTime = millis(); // Zapisanie czasu wciśnięcia
    } else if (millis() - buttonPressTime >= debounceTime) {
      if (lastButtonState == HIGH) {
        ledsOn = !ledsOn; // Przełączanie stanu diod LED
        if (ledsOn) {
          if (isBluetoothConnected && isBluetoothOverrideActive) {
            setColor(lastRed, lastGreen, lastBlue, lastWhite); // Ustawienie ostatniego koloru z Bluetooth
          } else {
            lastChangeTime = millis(); // Aktualizacja czasu ostatniej zmiany
          }
        } else {
          setColor(0, 0, 0, 0); // Wyłączenie diod LED
        }
        Serial.println(ledsOn ? "BUTTON_ON" : "BUTTON_OFF"); // Status LED-ów i przycisku
        delay(50); // Krótkie opóźnienie dla stabilności
      }
      lastButtonState = currentButtonState; // Aktualizacja stanu przycisku
    }
  } else {
    buttonPressTime = 0; // Resetowanie czasu wciśnięcia
    lastButtonState = currentButtonState; // Aktualizacja stanu przycisku
  }

  if (Serial.available() > 0) { // Jeśli są dane do odczytania z Bluetooth
    String incomingData = Serial.readStringUntil('\n'); // Odczyt danych do znaku nowej linii
    Serial.print("Received data: "); // Wyświetlanie informacji o otrzymanych danych
    Serial.println(incomingData); // Informacja o odebranych danych
    handleBluetooth(incomingData); // Przetwarzanie odebranych danych
  }

  if (!isBluetoothConnected && ledsOn && millis() - lastChangeTime >= 3000 && !isBluetoothOverrideActive) {
    changeColor(); // Zmiana koloru co 3 sekundy, jeśli Bluetooth nie jest połączony
    lastChangeTime = millis(); // Aktualizacja czasu ostatniej zmiany
  }

  if (isBluetoothConnected && !isBluetoothOverrideActive) {
    setColor(0, 0, 0, 0); // Wyłączenie diod LED, jeśli Bluetooth jest połączony, ale nie nadpisuje ustawień
  }
}

void handleBluetooth(String data) {
  if (data == "DISCONNECTED") {
    isBluetoothConnected = false; // Ustawienie flagi rozłączenia Bluetooth
    isBluetoothOverrideActive = false; // Dezaktywacja nadpisywania przez Bluetooth
    ledsOn = true; // Włączenie diod LED
    setColor(0, 0, 0, 0); // Wyłączenie diod LED (przygotowanie do automatycznej zmiany kolorów)
    lastChangeTime = millis(); // Aktualizacja czasu ostatniej zmiany
  } else if (data == "CONNECTED") {
    isBluetoothConnected = true; // Ustawienie flagi połączenia Bluetooth
    ledsOn = false; // Wyłączenie diod LED
    setColor(0, 0, 0, 0); // Wyłączenie diod LED
    Serial.println("BUTTON_OFF"); // Przycisk nie jest załączony
  } else if (isBluetoothConnected) {
    isBluetoothOverrideActive = true; // Aktywacja nadpisywania przez Bluetooth
    parseAndSetColor(data); // Parsowanie danych i ustawienie koloru
  }
}

void parseAndSetColor(String data) {
  int red = 0, green = 0, blue = 0, white = 0, brightness = 100; // Inicjalizacja zmiennych kolorów i jasności
  String values[5]; // Tablica do przechowywania wartości z danych
  int index = 0;

  while (data.length() > 0 && index < 5) {
    int spaceIndex = data.indexOf(' '); // Znalezienie pozycji spacji
    if (spaceIndex == -1) {
      values[index++] = data; // Przypisanie ostatniej wartości
      break;
    } else {
      values[index++] = data.substring(0, spaceIndex); // Przypisanie wartości przed spacją
      data = data.substring(spaceIndex + 1); // Usunięcie przetworzonej części danych
    }
  }

  if (index > 0) red = constrain(values[0].toInt(), 0, 255); // Konwersja i ograniczenie wartości czerwonego
  if (index > 1) green = constrain(values[1].toInt(), 0, 255); // Konwersja i ograniczenie wartości zielonego
  if (index > 2) blue = constrain(values[2].toInt(), 0, 255); // Konwersja i ograniczenie wartości niebieskiego
  if (index > 3) white = constrain(values[3].toInt(), 0, 255); // Konwersja i ograniczenie wartości białego
  if (index > 4) brightness = constrain(values[4].toInt(), 0, 100); // Konwersja i ograniczenie jasności

  red = map(red * brightness / 100, 0, 255, 0, 255); // Skalowanie czerwonego z uwzględnieniem jasności
  green = map(green * brightness / 100, 0, 255, 0, 255); // Skalowanie zielonego z uwzględnieniem jasności
  blue = map(blue * brightness / 100, 0, 255, 0, 255); // Skalowanie niebieskiego z uwzględnieniem jasności
  white = map(white * brightness / 100, 0, 255, 0, 255); // Skalowanie białego z uwzględnieniem jasności

  lastRed = red; // Zapisanie ostatniej wartości czerwonego
  lastGreen = green; // Zapisanie ostatniej wartości zielonego
  lastBlue = blue; // Zapisanie ostatniej wartości niebieskiego
  lastWhite = white; // Zapisanie ostatniej wartości białego
  lastBrightness = brightness; // Zapisanie ostatniej jasności

  ledsOn = true; // Włączenie diod LED
  setColor(red, green, blue, white); // Ustawienie koloru diod LED
  Serial.println("BUTTON_ON"); // Przycisk jest załączony
}

void setColor(int red, int green, int blue, int white) {
  analogWrite(redPin, constrain(red, 0, 255)); // Ustawienie jasności czerwonej diody
  analogWrite(greenPin, constrain(green, 0, 255)); // Ustawienie jasności zielonej diody
  analogWrite(bluePin, constrain(blue, 0, 255)); // Ustawienie jasności niebieskiej diody
  analogWrite(whitePin, constrain(white, 0, 255)); // Ustawienie jasności białej diody

  Serial.print("Setting color - R: ");  // Wyświetlanie ustawień koloru
  Serial.print(red); // Czerwony
  Serial.print(", G: ");
  Serial.print(green); // Zielony
  Serial.print(", B: ");
  Serial.print(blue); // Niebieski
  Serial.print(", W: ");
  Serial.println(white); // Biały
}

void changeColor() {
  static int currentColorIndex = 0; // Statyczna zmienna przechowująca indeks aktualnego koloru
  int colors[4][4] = { {255, 0, 0, 0}, {0, 255, 0, 0}, {0, 0, 255, 0}, {0, 0, 0, 255} }; // Tablica predefiniowanych kolorów

  setColor(colors[currentColorIndex][0], colors[currentColorIndex][1], colors[currentColorIndex][2], colors[currentColorIndex][3]); // Ustawienie koloru z tablicy
  currentColorIndex = (currentColorIndex + 1) % 4; // Przejście do następnego koloru
}
