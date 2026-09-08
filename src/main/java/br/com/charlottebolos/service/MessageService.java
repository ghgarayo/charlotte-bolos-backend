package br.com.charlottebolos.service;

public interface MessageService {

    String get(String chave);

    String getWithArgs(String chave, Object... args);

}
