package eu.siacs.auva.ui;

public interface UiInformableCallback<T> extends UiCallback<T> {
    void inform(String text);
}
