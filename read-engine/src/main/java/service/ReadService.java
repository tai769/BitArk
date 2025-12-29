package service;

import java.io.IOException;

public interface ReadService {
    void read(Long userId, Long msgId) throws IOException;


}
