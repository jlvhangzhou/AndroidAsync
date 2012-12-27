package com.koushikdutta.async.http;

import junit.framework.Assert;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.FilteredDataCallback;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.filter.ChunkedInputFilter;
import com.koushikdutta.async.http.filter.GZIPInputFilter;
import com.koushikdutta.async.http.filter.InflaterInputFilter;
import com.koushikdutta.async.http.libcore.RawHeaders;
import com.koushikdutta.async.http.server.UnknownRequestBody;

public class Util {
    public static AsyncHttpRequestBody getBody(RawHeaders headers) {
        String contentType = headers.get("Content-Type");
        if (UrlEncodedFormBody.CONTENT_TYPE.equals(contentType))
            return new UrlEncodedFormBody();
        return new UnknownRequestBody(contentType);
    }
    
    public static DataCallback getBodyDecoder(DataCallback callback, RawHeaders headers, final CompletedCallback reporter) {
        if ("gzip".equals(headers.get("Content-Encoding"))) {
            GZIPInputFilter gunzipper = new GZIPInputFilter();
            gunzipper.setDataCallback(callback);
            gunzipper.setCompletedCallback(reporter);
            callback = gunzipper;
        }        
        else if ("deflate".equals(headers.get("Content-Encoding"))) {
            InflaterInputFilter inflater = new InflaterInputFilter();
            inflater.setCompletedCallback(reporter);
            inflater.setDataCallback(callback);
            callback = inflater;
        }

        int _contentLength;
        try {
            _contentLength = Integer.parseInt(headers.get("Content-Length"));
        }
        catch (Exception ex) {
            _contentLength = -1;
        }
        final int contentLength = _contentLength;
        if (-1 != contentLength) {
            if (contentLength < 0) {
                reporter.onCompleted(new Exception("not using chunked encoding, and no content-length found."));
            }
//            System.out.println("Content len: " + contentLength);
            FilteredDataCallback contentLengthWatcher = new FilteredDataCallback() {
                int totalRead = 0;
                @Override
                public void onDataAvailable(DataEmitter emitter, ByteBufferList bb) {
                    totalRead += bb.remaining();
//                    System.out.println("read total: " + totalRead);
                    Assert.assertTrue(totalRead <= contentLength);
                    super.onDataAvailable(emitter, bb);
                    if (totalRead == contentLength) {
//                        System.out.println("content length is done");
                        reporter.onCompleted(null);
                    }
                }
            };
            contentLengthWatcher.setDataCallback(callback);
            contentLengthWatcher.setCompletedCallback(reporter);
            callback = contentLengthWatcher;
        }
        else if ("chunked".equalsIgnoreCase(headers.get("Transfer-Encoding"))) {
            ChunkedInputFilter chunker = new ChunkedInputFilter();
            
            chunker.setCompletedCallback(reporter);
            chunker.setDataCallback(callback);
            callback = chunker;
        }
        else {
            // we're done I guess.
            reporter.onCompleted(null);
        }
        return callback;
    }
}