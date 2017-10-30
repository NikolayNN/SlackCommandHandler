package ua.com.juja.slack.command.handler.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import ua.com.juja.slack.command.handler.exception.ParseSlackCommandException;


import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Sergey
 */
@ToString(exclude = {"slackNamePattern", "log"})
@EqualsAndHashCode
@Slf4j
public class SlackParsedCommand {
    private final String slackNamePattern = "@([a-zA-z0-9\\.\\_\\-]){1,21}";
    private UserData fromUserData;
    private String text;
    private List<UserData> usersInText;

    public SlackParsedCommand(UserData fromUserData, String text, List<UserData> usersInText) {
        this.fromUserData = fromUserData;
        this.text = text;
        this.usersInText = usersInText;

        log.debug("SlackParsedCommand created with parameters: " +
                        "fromSlackName: {} text: {} userCountInText {} users: {}",
                fromUserData, text, usersInText.size(), usersInText.toString());
    }

    public List<UserData> getAllUsersFromText() {
        return usersInText;
    }

    public UserData getFirstUserFromText() {
        if (usersInText.size() == 0) {
            log.warn("The text: '{}' doesn't contain any slack names", text);
            throw new ParseSlackCommandException(String.format("The text '%s' doesn't contain any slack names", text));
        } else {
            return usersInText.get(0);
        }
    }

    public String getTextWithoutSlackNames() {
        String result = text.replaceAll(slackNamePattern, "");
        result = result.replaceAll("\\s+", " ").trim();
        return result;
    }

    public UserData getFromUser() {
        return fromUserData;
    }

    public String getText() {
        return text;
    }

    public int getUserCountInText() {
        return usersInText.size();
    }

    public Map<String, UserData> getUsersWithTokens(Set<String> tokens) {
        log.debug("Recieve tokens: [{}] for searching. in the text: [{}]", tokens, text);
        List<Token> sortedTokenList = receiveTokensWithPositionInText(tokens);
        return findSlackNamesForTokensInText(sortedTokenList);
    }

    private List<Token> receiveTokensWithPositionInText(Set<String> tokens) {
        Set<Token> result = new TreeSet<>();
        for (String token : tokens) {
            if (!text.contains(token)) {
                throw new ParseSlackCommandException(String.format("Token '%s' didn't find in the string '%s'",
                        token, text));
            }
            int tokenCounts = text.split(token).length - 1;
            if (tokenCounts > 1) {
                throw new ParseSlackCommandException(String.format("The text '%s' contains %d tokens '%s', " +
                        "but expected 1", text, tokenCounts, token));
            }
            result.add(new Token(token, text.indexOf(token)));
        }
        return new ArrayList<>(result);
    }

    private Map<String, UserData> findSlackNamesForTokensInText(List<Token> sortedTokenList) {
        Map<String, UserData> result = new HashMap<>();

        for (int index = 0; index < sortedTokenList.size(); index++) {
            Token currentToken = sortedTokenList.get(index);
            Pattern pattern = Pattern.compile(slackNamePattern);
            Matcher matcher = pattern.matcher(text.substring(text.indexOf(currentToken.getToken())));
            if (matcher.find()) {
                String foundedSlackName = matcher.group().trim();
                int indexFoundedSlackName = text.indexOf(foundedSlackName);
                for (int j = index + 1; j < sortedTokenList.size(); j++) {
                    if (indexFoundedSlackName > sortedTokenList.get(j).getPositionInText()) {
                        log.warn("The text: [{}] doesn't contain slack name for token: [{}]",
                                text, currentToken.getToken());
                        throw new ParseSlackCommandException(String.format("The text '%s' doesn't contain slackName " +
                                "for token '%s'", text, currentToken.getToken()));
                    }
                }
                addFoundedSlackNameToResult(currentToken, foundedSlackName, result);
            } else {
                log.warn("The text: [{}] doesn't contain slack name for token: [{}]",
                        text, sortedTokenList.get(index).getToken());
                throw new ParseSlackCommandException(String.format("The text '%s' " +
                        "doesn't contain slackName for token '%s'", text, sortedTokenList.get(index).getToken()));
            }
        }
        return result;
    }

    private void addFoundedSlackNameToResult(Token currentToken, String foundedSlackName, Map<String, UserData> result) {
        for (UserData item : usersInText) {
            if (item.getSlack().equals(foundedSlackName)) {
                log.debug("Found user: {} for token:", item, currentToken.getToken());
                result.put(currentToken.getToken(), item);
            }
        }
    }

    @AllArgsConstructor
    @Getter
    private final class Token implements Comparable {
        private final String token;
        private final int positionInText;

        @Override
        public int compareTo(Object object) {
            Token thatToken = (Token) object;
            return positionInText - thatToken.getPositionInText();
        }
    }
}