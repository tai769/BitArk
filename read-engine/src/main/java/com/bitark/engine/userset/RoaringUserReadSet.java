package com.bitark.engine.userset;

import java.io.DataInputStream;
import java.io.DataOutputStream;

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

    @Override
    public void toSnapshot(DataOutputStream out) {
        try{
            bitmap.serialize(out);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void loadSnapshot(DataInputStream in) {
       try{
           bitmap.deserialize(in);
       }catch (Exception e){
           e.printStackTrace();
          }
       }
}


