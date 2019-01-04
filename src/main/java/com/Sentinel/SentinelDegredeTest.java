package com.Sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotEntryCallback;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.statistic.StatisticSlotCallbackRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author luxuanyu
 * @version 1.0
 * @desc
 * @since 2019/1/3 16:52
 */
@RunWith(JUnit4.class)
public class SentinelDegredeTest {

    private int threadCount = 2;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private volatile boolean isStop = false;

    private AtomicInteger pass = new AtomicInteger(0);
    private AtomicInteger total = new AtomicInteger(0);
    private AtomicInteger block = new AtomicInteger(0);

    private volatile int seconds = 30;

    CountDownLatch countDownLatch = new CountDownLatch(1);

    @Test
    public void degredeException() {

        /*
         * 添加entry回调，实际回调时不会根据key来回调而是所有已注册的事件都会回调
         * 如果要执行特定resource的回调，可以在ProcessorSlotEntryCallback实现类的onPass或者onSuccess中实现
         */
        StatisticSlotCallbackRegistry.addEntryCallback("degrade1",new CallBack());

        //初始化规则
        initDegredeExRule();

        //初始化统计
        initStatisticTask();

        for (int i = 0; i < 1; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while(!isStop){

                        Entry entry = null;
                        try{
                            Thread.sleep(10);
                            entry = SphU.entry("degrade");
                            int i = 1/0;
                            pass.incrementAndGet();
                        }catch (Exception e){
                            Tracer.trace(e);
                            block.incrementAndGet();
                        }finally {
                            total.incrementAndGet();
                            if (entry != null) {
                                entry.exit();
                            }
                        }
                    }
                }
            }).start();
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

    @Test
    public void degredeRt() {

        /*
         * 添加entry回调，实际回调时不会根据key来回调而是所有已注册的事件都会回调
         * 如果要执行特定resource的回调，可以在ProcessorSlotEntryCallback实现类的onPass或者onSuccess中实现
         * 注册回调时，StatisticSlot会回调所有的ProcessorSlotEntryCallback实例，应该在回调方法做‘限流’控制
         */
        StatisticSlotCallbackRegistry.addEntryCallback("degrade1",new CallBack());

        //初始化规则
        initDegredeRtRule();

        //初始化统计
        initStatisticTask();

        for (int i = 0; i < 1; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while(!isStop){

                        Entry entry = null;
                        try{
                            Thread.sleep(10);
                            entry = SphU.entry("degrade");
                            Thread.sleep(100);
                            pass.incrementAndGet();
                        }catch (Exception e){
                            Tracer.trace(e);
                            block.incrementAndGet();
                        }finally {
                            total.incrementAndGet();
                            if (entry != null) {
                                entry.exit();
                            }
                        }
                    }
                }
            }).start();
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

    private void initStatisticTask() {
        Thread timer = new Thread(new StatisticTask());
        timer.setDaemon(true);
        timer.start();
    }



    class StatisticTask extends TimerTask {

        @Override
        public void run() {
            int lastSecondTotal = 0;
            int lastSecondPass = 0;
            int lastSecondBlock = 0;

            while (!isStop) {

                lastSecondTotal = total.get() -  lastSecondTotal;
                lastSecondPass = pass.get() - lastSecondPass;
                lastSecondPass = lastSecondPass < 0 ? 0 : lastSecondPass;
                lastSecondBlock = block.get() - lastSecondBlock;
                lastSecondBlock = lastSecondBlock < 0 ? 0 : lastSecondBlock;

                total = new AtomicInteger(0);
                pass = new AtomicInteger(0);
                block = new AtomicInteger(0);


                System.out.println(dateFormat.format(new Date())+" ,pass="+lastSecondPass+",block="+lastSecondBlock+",total="+lastSecondTotal);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (seconds--<=1){
                    isStop = true;
                    countDownLatch.countDown();
                }

            }
            System.out.println(dateFormat.format(new Date())+" ,pass="+lastSecondPass+",block="+lastSecondBlock+",total="+lastSecondTotal);
        }
    }

    class CallBack implements ProcessorSlotEntryCallback{

        @Override
        public void onPass(Context context, ResourceWrapper resourceWrapper, Object param, int count, Object... args) throws Exception {
            System.out.println("pass");
        }

        @Override
        public void onBlocked(BlockException ex, Context context, ResourceWrapper resourceWrapper, Object param, int count, Object... args) {
            System.out.println(context.getCurEntry().getCurNode().exceptionQps());
        }
    }

    private void initDegredeRtRule() {
        List<DegradeRule> rules = new ArrayList<DegradeRule>();
        DegradeRule rule = new DegradeRule("degrade");
        rule.setCount(10);
        rule.setTimeWindow(3);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_RT);

        rules.add(rule);
        DegradeRuleManager.loadRules(rules);
    }

    private void initDegredeExRule() {
        List<DegradeRule> rules = new ArrayList<DegradeRule>();
        DegradeRule rule = new DegradeRule("degrade");
        rule.setCount(10);
        rule.setTimeWindow(3);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT);

        rules.add(rule);
        DegradeRuleManager.loadRules(rules);
    }

    public static void main(String[] args) {
        int s =1;
        System.out.println(--s>=1);
    }

}
