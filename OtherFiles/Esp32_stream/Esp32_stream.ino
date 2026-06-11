#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#include "emg_playback_data2.h" 

// ─── EDITAR ACA ────

#define MODE_PRUEBA  1
#define MODE_CAPTURA 2
const int MODO = MODE_CAPTURA;

#define FS_100HZ  100
#define FS_2000HZ 2000
const int SAMPLING_RATE = FS_100HZ;

#define CH1_PIN 4
#define CH2_PIN 5
#define CH3_PIN 6

// ─── LEDs ────

#define LED_POWER_PIN 11   // Indica encendido
#define LED_BLE_PIN   12   // Indica BLE conectado

// ─── VARIABLES Y UUID ─────

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

BLECharacteristic* pCharacteristic = nullptr;
bool deviceConnected = false;

uint32_t interval_us  = 1000000 / SAMPLING_RATE;
uint32_t lastSample   = 0;
uint32_t playbackIndex = 0;

// ─── CALLBACKS BLE ─────

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    digitalWrite(LED_BLE_PIN, HIGH);
    Serial.println("BLE: cliente conectado");
  }
  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    digitalWrite(LED_BLE_PIN, LOW);
    Serial.println("BLE: cliente desconectado");
    BLEDevice::startAdvertising();
  }
};

// ─── SETUP ─────

void setup() {
  Serial.begin(115200);
  Serial.println("Iniciando BLE EMG Server...");

  // LED
  pinMode(LED_POWER_PIN, OUTPUT);
  pinMode(LED_BLE_PIN,   OUTPUT);
  digitalWrite(LED_POWER_PIN, HIGH);  // Encender al conectar
  digitalWrite(LED_BLE_PIN,   LOW);   // Apagado hasta conectar BLE

  // Configurar ADC
  if (MODO == MODE_CAPTURA) {
    analogReadResolution(12);
    analogSetAttenuation(ADC_11db);
  }

  // Inicializar BLE
  BLEDevice::init("EMG_ESP32");

  BLEServer* pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService* pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
    CHARACTERISTIC_UUID,
    BLECharacteristic::PROPERTY_NOTIFY
  );

  pCharacteristic->addDescriptor(new BLE2902());

  pService->start();

  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  BLEDevice::startAdvertising();

  Serial.println("BLE listo. Esperando conexion...");
}

// ─── LOOP ────

void loop() {
  uint32_t now = micros();
  if (now - lastSample < interval_us) return;
  lastSample = now;

  if (!deviceConnected) return;

  int16_t ch1, ch2, ch3;

  if (MODO == MODE_PRUEBA) {
    ch1 = emg_data[playbackIndex][0];
    ch2 = emg_data[playbackIndex][1];
    ch3 = emg_data[playbackIndex][2];
    playbackIndex = (playbackIndex + 1) % EMG_PLAYBACK_LEN;

  } else {
    float v1 = analogRead(CH1_PIN) * 3.3f / 4095.0f - 1.65f;
    float v2 = analogRead(CH2_PIN) * 3.3f / 4095.0f - 1.65f;
    float v3 = analogRead(CH3_PIN) * 3.3f / 4095.0f - 1.65f;

    ch1 = (int16_t)(v1 * EMG_SCALE_FACTOR);
    ch2 = (int16_t)(v2 * EMG_SCALE_FACTOR);
    ch3 = (int16_t)(v3 * EMG_SCALE_FACTOR);
  }

  uint8_t packet[6];
  packet[0] = (uint8_t)(ch1 & 0xFF);
  packet[1] = (uint8_t)((ch1 >> 8) & 0xFF);
  packet[2] = (uint8_t)(ch2 & 0xFF);
  packet[3] = (uint8_t)((ch2 >> 8) & 0xFF);
  packet[4] = (uint8_t)(ch3 & 0xFF);
  packet[5] = (uint8_t)((ch3 >> 8) & 0xFF);

  pCharacteristic->setValue(packet, 6);
  pCharacteristic->notify();
}