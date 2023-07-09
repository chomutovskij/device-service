package com.achomutovskij.deviceservice.gsm;

import org.immutables.value.Value;

@Value.Immutable
public interface GsmNetworkDetails {
    String technology();

    String twoGBands();

    String threeGBands();

    String fourGBands();
}
