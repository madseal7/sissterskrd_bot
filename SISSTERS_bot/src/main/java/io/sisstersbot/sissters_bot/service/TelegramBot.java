package io.sisstersbot.sissters_bot.service;

import io.sisstersbot.sissters_bot.config.BotConfig;
import io.sisstersbot.sissters_bot.model.User;
import io.sisstersbot.sissters_bot.model.UserRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.sql.Timestamp;
import java.util.*;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    @Autowired
    private UserRepository userRepository;
    final BotConfig config;

    static final String INFO_TEXT = "КиберШкола SISSTERS – это отдельное довочковое направление глобального проекта KIBERone, " +
            "который признан ООН и ЮНЕСКО ЛУЧШИМ В МИРЕ в сфере цифрового образования детей. \n \n" +
            "Также является лучшим в Евросоюзе и аккредитован корпорацией Microsoft и SAMSUNG. \uD83D\uDCBB \n \n" +
            "Работает более чем в 27 странах и более чем в 350 городах. \uD83D\uDD25 \n \n Поступить можно в любое время в течение года, при наличии свободных мест. \uD83C\uDF93 \n \n" +
            "Группы набираются каждую неделю, поэтому ребенок может поступить и вместе с другими детьми и начать изучать программу, соответствующую своему возрасту. \n\nУченицы в течение 2 или 3 лет погружаются в программирование, 3D-моделирование, создание сайтов и приложений, анимацию, моушен-дизайн, фото/видеомонтаж и финансовую грамотность и в итоге получают сертификат международного образца.\n" +
            "\n" +
            "А еще здесь проводят интересные мастер-классы и устраивают КИБЕРквест — бесплатный пробный урок. Там девочки: \n" +
            "\uD83D\uDD38 запрограммируют героев Minecraft\n" +
            "\uD83D\uDD38 создадут анимационный ролик\n" +
            "\uD83D\uDD38 получат полезный перекус\n" +
            "\uD83D\uDD38 познакомятся с профессиями будущего \n \n Наш адрес : \n г. Краснодар, ул. Героя Пешкова 14к2 \n Телефон : +7(918)023-05-93";

    static final String MEDIA = "Наши социальные сети : \n\n Instagram : https://instagram.com/sissters_krd?igshid=MjEwN2IyYWYwYw== \uD83D\uDCA5 \n\n VK : https://vk.com/sissters_krasnodar \uD83D\uDD25 \n\n WhatsApp : https://chat.whatsapp.com/DQgEwcYaLgMJQuXRqXr8gK \uD83C\uDF08";
    public TelegramBot(BotConfig config){
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/info", "Информация о нас"));
        listOfCommands.add(new BotCommand("/lesson", "Записаться на пробный урок"));
        listOfCommands.add(new BotCommand("/media", "Социальные сети"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Ошибка в списке команд бота: " + e.getMessage());
        }
    }
    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }
    private Map<Long, Boolean> emailSentMap = new HashMap<>();

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            switch (messageText){
                case "/start" :
                        registerUser(update.getMessage());
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        break;
                case "/info" :
                        sendMessage(chatId, INFO_TEXT);
                        break;
                case "/lesson" :
                        addPhoneNumberButton(chatId);
                        break;
                case "/media" :
                        sendMessage(chatId, MEDIA);
                        break;
                default: sendMessage(chatId, "Упс..., я вас не поняла. Пожалуйста, выберите команду из меню слева \uD83D\uDE07");
            }
        } else if (update.hasMessage() && update.getMessage().hasAudio() || update.getMessage().hasDocument() || update.getMessage().hasPhoto() || update.getMessage().hasPoll() || update.getMessage().hasLocation()){
            long chatId = update.getMessage().getChatId();
            sendMessage(chatId, "Упс..., я вас не поняла. Пожалуйста, выберите команду из меню слева \uD83D\uDE07");
        }
        else if (update.hasMessage() && update.getMessage().hasContact()) {
            Contact contact = update.getMessage().getContact();
            String phoneNumber = contact.getPhoneNumber();
            String firstName = contact.getFirstName();
            String lastName = contact.getLastName();
            long chatId = update.getMessage().getChatId();
            if (!emailSentMap.containsKey(chatId)){
            sendEmail("Новая регистрация на пробный урок", "Номер телефона : " + "+" + phoneNumber + "\nИмя матери : " + firstName + " " + lastName);
            emailSentMap.put(chatId, true);
            sendMessage(chatId, "Ваш номер телефона передан администратору. Пожалуйста, ожидайте звонка \uD83E\uDD16");
            } else sendMessage(chatId, "Ваш номер уже был передан администратору, не ломайте кнопку, пожалуйста =) ");
        }
            }


    private void registerUser(Message msg) {
        if(userRepository.findById(msg.getChatId()).isEmpty()){

            var chatId = msg.getChatId();
            var chat = msg.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegistredAt(new Timestamp(System.currentTimeMillis()));
            userRepository.save(user);
            log.info("Был сохранён пользователь:" + user);
        }
    }

    private void startCommandReceived(long chatId, String name){
        String answer = "Здравствуйте, " + name + ", меня зовут Саша. Я ваш виртуальный ассистент для записи на пробный урок, выберите интересующие вас команды из меню слева =)";

        log.info("Бот ответил пользователю по имени: " + name);

        sendMessage(chatId, answer);
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);

        try{
            execute(message);
        }
        catch(TelegramApiException e) {
            log.error("Произошла ошибка: " + e.getMessage());
        }
    }
    public void addPhoneNumberButton(long chatId) {
        // Создаем сообщение
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Нажмите на кнопку \"Отправить номер телефона\" для записи на пробный урок");

        // Создаем кнопку для запроса номера телефона
        KeyboardButton requestContactButton = new KeyboardButton("Отправить номер телефона");
        requestContactButton.setRequestContact(true);

        // Создаем клавиатуру с одной кнопкой
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setOneTimeKeyboard(true);
        keyboardMarkup.setResizeKeyboard(true);
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add(requestContactButton);
        keyboardRows.add(row);
        keyboardMarkup.setKeyboard(keyboardRows);

        // Добавляем клавиатуру в сообщение
        message.setReplyMarkup(keyboardMarkup);

        try{
            execute(message);
        }
        catch(TelegramApiException e) {
            log.error("Произошла ошибка: " + e.getMessage());
        }
    }

        public static void sendEmail(String subject, String message) throws Exception {
            // Настройки для подключения к серверу SMTP
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.mail.ru");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.auth", "true");



            // Адрес отправителя и пароль
            final String username = "sissters_bot@mail.ru";
            String to = "e.mila-23@mail.ru";
            String password = "5yrVGrqqCz7SYBwftThJ";

            // Создание сессии для отправки сообщения
            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                        protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                            return new javax.mail.PasswordAuthentication(username, password);
                        }
                    });

            // Создание сообщения
            MimeMessage emailMessage = new MimeMessage(session);
            emailMessage.setFrom(new InternetAddress(username));
            emailMessage.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
            emailMessage.setSubject(subject);
            emailMessage.setText(message);

            // Отправка сообщения
            Transport.send(emailMessage);


        }
    }



