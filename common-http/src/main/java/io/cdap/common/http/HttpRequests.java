/*
 * Copyright © 2014-2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.common.http;

import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import io.cdap.common.ContentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Executes {@link HttpRequest}s and returns an {@link HttpResponse}.
 */
public final class HttpRequests {
  private static final Logger LOG = LoggerFactory.getLogger(HttpRequests.class);

  private static final AtomicReference<SSLSocketFactory> TRUST_ALL_SSL_FACTORY =
    new AtomicReference<SSLSocketFactory>();

  private HttpRequests() { }

  /**
   * Executes an HTTP request to the url provided.
   *
   * @param request HTTP request to execute
   * @param requestConfig configuration for the HTTP request to execute
   * @return HTTP response
   */
  public static HttpResponse execute(HttpRequest request, HttpRequestConfig requestConfig) throws IOException {
    HttpURLConnection conn = getConnection(request, requestConfig);
    conn.connect();

    ContentProvider<? extends InputStream> bodySrc = request.getBody();
    if (bodySrc != null) {
      try (InputStream input = bodySrc.getInput(); OutputStream os = conn.getOutputStream()) {
        ByteStreams.copy(input, os);
      }
    }
    return request.hasContentConsumer() ? new HttpResponse(conn, request.getConsumer()) : new HttpResponse(conn);
  }

  private static HttpURLConnection getConnection(HttpRequest request, HttpRequestConfig requestConfig)
    throws IOException {
    String requestMethod = request.getMethod().name();
    URL url = request.getURL();

    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod(requestMethod);
    conn.setReadTimeout(requestConfig.getReadTimeout());
    conn.setConnectTimeout(requestConfig.getConnectTimeout());

    Multimap<String, String> headers = request.getHeaders();
    if (headers != null && !headers.isEmpty()) {
      for (Map.Entry<String, String> header : headers.entries()) {
        conn.setRequestProperty(header.getKey(), header.getValue());
      }
    }

    if (request.getBody() != null) {
      conn.setDoOutput(true);
      Long bodyLength = request.getBodyLength();
      if (bodyLength != null) {
        // use intValue to support 1.6
        if (bodyLength > requestConfig.getFixedLengthStreamingThreshold()) {
          conn.setFixedLengthStreamingMode(bodyLength.intValue());
        }
      } else {
        conn.setChunkedStreamingMode(0);
      }
    }

    if (conn instanceof HttpsURLConnection && !requestConfig.isVerifySSLCert()) {
      // Certificate checks are disabled for HTTPS connection.
      LOG.trace("Disabling SSL certificate check for {}", request.getURL());
      try {
        disableCertCheck((HttpsURLConnection) conn);
      } catch (Exception e) {
        LOG.error("Got exception while disabling SSL certificate check for {}", request.getURL());
      }
    }
    return conn;
  }

  /**
   * Executes an HTTP request with default request configuration.
   *
   * @param request HTTP request to execute
   * @return HTTP response
   * @throws java.io.IOException
   */
  public static HttpResponse execute(HttpRequest request) throws IOException {
    return execute(request, HttpRequestConfig.DEFAULT);
  }

  private static boolean isSuccessful(int responseCode) {
    return 200 <= responseCode && responseCode < 300;
  }

  public static void disableCertCheck(HttpsURLConnection conn)
    throws NoSuchAlgorithmException, KeyManagementException {
    if (TRUST_ALL_SSL_FACTORY.get() == null) {
      SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, new TrustManager[]{
        new X509TrustManager() {
          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }

          @Override
          public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
            throws CertificateException {
            // Trust all
          }

          @Override
          public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
            throws CertificateException {
            // Trust all
          }
        }
      }, new SecureRandom());

      TRUST_ALL_SSL_FACTORY.compareAndSet(null, sslContext.getSocketFactory());
    }

    conn.setSSLSocketFactory(TRUST_ALL_SSL_FACTORY.get());
    conn.setHostnameVerifier(TRUST_ALL_HOSTNAME_VERIFIER);
  }

  private static final HostnameVerifier TRUST_ALL_HOSTNAME_VERIFIER =
    new HostnameVerifier() {
      @Override
      public boolean verify(String hostname, SSLSession session) {
        return true;
      }
    };
}
