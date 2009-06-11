// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.sdk.internal.transport;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * A transport message encapsulating the data sent/received over the wire
 * (protocol headers and content). This class can serialize and deserialize
 * itself into a BufferedWriter according to the Chrome Developer Tools Protocol
 * specification.
 */
public class Message {

  /**
   * This exception is thrown during Message deserialization whenever the input
   * is malformed.
   */
  public static class MalformedMessageException extends Exception {

    private static final long serialVersionUID = 1L;

    public MalformedMessageException() {
      super();
    }

    public MalformedMessageException(String message) {
      super(message);
    }

    public MalformedMessageException(Throwable cause) {
      super(cause);
    }

    public MalformedMessageException(String message, Throwable cause) {
      super(message, cause);
    }

  }

  /**
   * Known Chrome Developer Tools Protocol headers (ToolHandler implementations
   * can add their own headers.)
   */
  public enum Header {
    CONTENT_LENGTH("Content-Length"),
    TOOL("Tool"),
    DESTINATION("Destination"), ;

    public String name;

    Header(String value) {
      this.name = value;
    }
  }

  /**
   * The end of protocol header line.
   */
  private static final String HEADER_TERMINATOR = "\r\n";

  private final HashMap<String, String> headers;

  private final String content;

  public Message(Map<String, String> headers, String content) {
    this.headers = new HashMap<String, String>(headers);
    this.content = content;
    this.headers.put(Header.CONTENT_LENGTH.name,
        String.valueOf(content == null ? 0 : content.length()));
  }

  /**
   * Sends a message through the specified writer.
   *
   * @param writer to send the message through
   * @throws IOException
   */
  public void sendThrough(BufferedWriter writer) throws IOException {
    String content = maskNull(this.content);
    for (Map.Entry<String, String> entry : this.headers.entrySet()) {
      writeNonEmptyHeader(writer, entry.getKey(), entry.getValue());
    }
    writer.write(HEADER_TERMINATOR);
    if (content.length() > 0) {
      writer.write(content);
    }
    writer.flush();
  }

  /**
   * Reads a message from the specified reader.
   *
   * @param reader to read message from
   * @return a new message, or {@code null} if input invalid (end-of-stream or
   *         bad message format)
   * @throws IOException
   * @throws MalformedMessageException if the input does not represent a valid
   *         message
   */
  public static Message fromBufferedReader(BufferedReader reader)
      throws IOException, MalformedMessageException {
    Map<String, String> headers = new HashMap<String, String>();
    synchronized (reader) {
      while (true) { // read headers
        String line = reader.readLine();
        if (line == null) {
          return null;
        }
        if (line.length() == 0) {
          break; // end of headers
        }
        String[] nameValue = line.split(":", 2);
        if (nameValue.length != 2) {
          return null;
        } else {
          headers.put(nameValue[0], nameValue[1]);
        }
      }

      // Read payload if applicable
      int contentLength = Integer.valueOf(getHeader(headers, Header.CONTENT_LENGTH.name, "0"));
      char[] content = new char[contentLength];
      int totalRead = 0;
      while (totalRead < contentLength) {
        int readBytes = reader.read(content, totalRead, contentLength - totalRead);
        if (readBytes == -1) {
          // End-of-stream (browser closed?)
          return null;
        }
        totalRead += readBytes;
      }

      // Construct response message
      String contentString = new String(content);
      return new Message(headers, contentString);
    }
  }

  /**
   * @return the "Tool" header value
   */
  public String getTool() {
    return getHeader(headers, Header.TOOL.name, null);
  }

  /**
   * @return the "Destination" header value
   */
  public String getDestination() {
    return getHeader(headers, Header.DESTINATION.name, null);
  }

  /**
   * @return the message content. Never {@code null} (for no content, returns an
   *         empty String)
   */
  public String getContent() {
    return content;
  }

  /**
   * @param name of the header
   * @param defaultValue to return if the header is not found in the message
   * @return the {@code name} header value or {@code defaultValue} if the header
   *         is not found in the message
   */
  public String getHeader(String name, String defaultValue) {
    return getHeader(this.headers, name, defaultValue);
  }

  private static String getHeader(Map<? extends String, String> headers, String headerName,
      String defaultValue) {
    String value = headers.get(headerName);
    if (value == null) {
      value = defaultValue;
    }
    return value;
  }

  private String maskNull(String string) {
    return string == null
        ? ""
        : string;
  }

  private void writeNonEmptyHeader(BufferedWriter writer, String headerName, String headerValue)
      throws IOException {
    if (headerValue != null) {
      writer.write(buildHeader(headerName, headerValue));
    }
  }

  @Override
  public String toString() {
    StringWriter sw = new StringWriter();
    try {
      this.sendThrough(new BufferedWriter(sw));
    } catch (IOException e) {
      // never occurs
    }
    return sw.toString();
  }

  private static String buildHeader(String name, String value) {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(':').append(value).append(HEADER_TERMINATOR);
    return sb.toString();
  }
}
