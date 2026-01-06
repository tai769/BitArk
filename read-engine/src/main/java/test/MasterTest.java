package test;

import service.ReadServiceImpl;

public class MasterTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== 开始测试 ReadService ===");
        ReadServiceImpl readService = new ReadServiceImpl();

        readService.read(1L, 1L);
        System.out.println("=== 测试完成 ===");
    }
}
