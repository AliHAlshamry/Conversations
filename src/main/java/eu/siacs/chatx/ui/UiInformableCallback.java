package eu.siacs.chatx.ui;

public interface UiInformableCallback<T> extends UiCallback<T> {
    void inform(String text);
}
