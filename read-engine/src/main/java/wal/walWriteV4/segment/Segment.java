package wal.walWriteV4.segment;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class Segment implements AutoCloseable {
    public final Path path;
    public final long baseOffset;
    public final long size;
    public final RandomAccessFile raf;
    public final FileChannel channel;
    public final MappedByteBuffer mmap;
    public Segment(Path path, long baseOffset, long size) throws IOException {
        this.path = path;
        this.baseOffset = baseOffset;
        this.size = size;
        File file = path.toFile();
        if (!file.getParentFile().exists()){
            file.getParentFile().mkdirs();
        }
        try {
            RandomAccessFile r = new RandomAccessFile(file, "rw");
            if (r.length() < size) {
                r.setLength(size);
            }
        } catch (IOException e) {
            throw new IOException("Failed to create RandomAccessFile for " + path, e);
        }
        this.raf = new RandomAccessFile(file, "rw");
        this.channel = raf.getChannel();
        this.mmap = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
    }
    @Override
    public void close() throws Exception {
        channel.close();
        raf.close();
    }
}
