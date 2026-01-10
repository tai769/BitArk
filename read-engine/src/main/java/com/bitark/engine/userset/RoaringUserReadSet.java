package com.bitark.engine.userset;

import org.roaringbitmap.RoaringBitmap;

public class RoaringUserReadSet implements UserReadSet {

    private final RoaringBitmap bitmap = new RoaringBitmap();


    @Override
    public void mark(long msgId) {
        bitmap.add((int) msgId);    
      
    }

    @Override
    public boolean isRead(long msgId) {
        return bitmap.contains((int) msgId);
    }

}
