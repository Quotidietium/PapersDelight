package dev.tako.papersdelight.jug;

import java.util.function.BooleanSupplier;

final class JugDeliveryFlow {
    interface Scheduler { Handle entityLater(Runnable task, Runnable retired, long delay); void region(Runnable task, Runnable retired); void retain(Runnable pending); }
    interface Handle { boolean isCancelled(); }
    private final Scheduler scheduler; private final Runnable consume; private final BooleanSupplier online;
    private final Runnable payout; private final Runnable rollback; private final Runnable drop;
    private int state;
    private boolean taskPending; private boolean retiredPending; private boolean fullCompensation;

    JugDeliveryFlow(Scheduler scheduler, Runnable consume, BooleanSupplier online, Runnable payout, Runnable rollback, Runnable drop) {
        this.scheduler=scheduler; this.consume=consume; this.online=online; this.payout=payout; this.rollback=rollback; this.drop=drop;
    }
    boolean register() {
        Handle h=scheduler.entityLater(this::dispatch,this::retire,1L); boolean dispatchAfter;
        synchronized(this) {
            if(retiredPending||h==null||h.isCancelled()){ state=3; scheduleCompensation(false); return false; }
            state=1;
            try { consume.run(); } catch(Throwable x){ state=3; scheduleCompensation(false); return false; }
            dispatchAfter=taskPending;
        }
        if(dispatchAfter) dispatch(); return true;
    }
    private void dispatch() {
        synchronized(this){ if(state==0){taskPending=true;return;} if(state!=1)return; state=2; }
        if(!online.getAsBoolean()){ beginCompensation(); return; }
        try{ payout.run(); }catch(Throwable x){ beginCompensation(); }
    }
    private void retire(){
        synchronized(this){
            if(state==0){ retiredPending=true; return; }
            if(state!=1)return;
            state=3; fullCompensation=true;
        }
        scheduleCompensation(true);
    }
    private void beginCompensation(){ synchronized(this){if(state==3)return;state=3;fullCompensation=true;} scheduleCompensation(true); }
    private void scheduleCompensation(boolean full){
        scheduler.region(() -> { rollback.run(); if(full) drop.run(); }, () -> scheduler.retain(() -> scheduleCompensation(full)));
    }
}
