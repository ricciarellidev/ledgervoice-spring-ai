package dev.ricciarelli.ledgervoice.application;

public interface AudioGateway {
    String transcribe(byte[] audio, String filename);
    byte[] speak(String text);
}
