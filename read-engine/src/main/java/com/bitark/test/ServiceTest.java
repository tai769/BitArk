package com.bitark.test;

import com.bitark.service.ReadServiceImpl;



public class ServiceTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 开始测试 ReadService ===");
      //  ReadServiceImpl readService = new ReadServiceImpl();

       /* try{

            boolean isRead = readService.isRead(1L, 1L);
            System.out.println("   结果: " + (isRead ? "已读" : "未读"));
            System.out.println("\n1. 标记消息为已读: userId=1, msgId=1");
            readService.read(1L, 1L);
            System.out.println("   标记成功");

            System.out.println("\n2. 检查是否已读");
            isRead = readService.isRead(1L, 1L);
            System.out.println("   结果: " + (isRead ? "已读" : "未读"));

            System.out.println("\n=== 测试完成 ===");
        } catch (Exception e) {
            System.err.println("\n!!! 测试失败 !!!");
            e.printStackTrace();
        }

        */

        try {
            System.out.println("=== 恢复数据 ===");
         //   readService.recover();
            System.out.println("=== 恢复完成 ===");
            System.out.println("\n1. 检查是否已读");
        //    boolean isRead = readService.isRead(1L, 1L);
        //    System.out.println("   结果: " + (isRead ? "已读" : "未读"));
        }catch (Exception e){
            System.err.println("\n!!! 恢复失败 !!!");
        }



        
    }

}
