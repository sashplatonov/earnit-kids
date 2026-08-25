package com.sashplatonov.earnit.kids.platform.webpush;

public class WebPushTransportException extends Exception {
  private final int status;

  public WebPushTransportException(int status) {
    this.status = status;
  }

  public int status() {
    return status;
  }
}
