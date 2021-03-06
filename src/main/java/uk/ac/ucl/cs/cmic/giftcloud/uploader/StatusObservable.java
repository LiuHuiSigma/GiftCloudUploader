/*=============================================================================

  GIFT-Cloud: A data storage and collaboration platform

  Copyright (c) University College London (UCL). All rights reserved.
  Released under the Modified BSD License
  github.com/gift-surg

  Author: Tom Doel
=============================================================================*/

package uk.ac.ucl.cs.cmic.giftcloud.uploader;

import java.util.ArrayList;

public class StatusObservable<T> {
    private final java.util.List<StatusListener<T>> listeners = new ArrayList<StatusListener<T>>();

    public void addListener(final StatusListener<T> listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public interface StatusListener<T> {
        void statusChanged(final T visibility);
    }

    protected void notifyStatusChanged(final T status) {
        for (final StatusListener<T> listener : listeners) {
            listener.statusChanged(status);
        }
    }
}
