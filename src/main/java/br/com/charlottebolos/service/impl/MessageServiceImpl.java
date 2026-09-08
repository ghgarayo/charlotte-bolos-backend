package br.com.charlottebolos.service.impl;

import br.com.charlottebolos.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageSource messageSource;

    @Override
    public String get(String chave) {
        return messageSource.getMessage(chave, null, LocaleContextHolder.getLocale());
    }

    public String getWithArgs(String chave, Object... args) {
        return messageSource.getMessage(chave, args, LocaleContextHolder.getLocale());
    }
}
