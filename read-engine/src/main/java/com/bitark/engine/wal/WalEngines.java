package com.bitark.engine.wal;

import com.bitark.enums.WalMode;
import com.bitark.engine.adapter.GroupCommitWalEngine;
import com.bitark.engine.config.WalConfig;

import java.io.IOException;


public class WalEngines {




    public static WalEngine createEngine(WalConfig config) throws IOException {
        WalMode mode = config.getWalMode();
      

        switch (mode) {
   
            case GROUP_COMMIT:
                return new GroupCommitWalEngine(config);
        
            default:
                throw new IllegalArgumentException("Unknown WalMode: " + mode);
        }
    }

}
