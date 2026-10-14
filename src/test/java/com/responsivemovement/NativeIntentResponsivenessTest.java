package com.responsivemovement;

import com.responsivemovement.NpcApproachControllerTest.Fixture;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeIntentResponsivenessTest
{
    private static LocalPoint p(int x,int y) { return new LocalPoint(x,y,0); }

    @Test
    public void allEightNewestInteractionTimelinesRespondWithoutAnIntermediateWait() throws Exception
    {
        List<String[]> rows = new ArrayList<>(), maps = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
            "native-intent-2359.txt"),StandardCharsets.UTF_8)))
        {
            reader.lines().filter(line->!line.startsWith("#")).forEach(line->{
                String[] fields=line.split(" "); (fields[0].equals("M")?maps:rows).add(fields);
            });
        }
        for (int index=0;index<rows.size();)
        {
            String[] capture=rows.get(index++);
            List<String[]> events=new ArrayList<>();
            while(index<rows.size()&&!rows.get(index)[0].equals("B")) { events.add(rows.get(index++)); }
            events.sort(Comparator.comparingLong(row->Long.parseLong(row[1])));
            String[] first=events.get(0);
            long clickTime=events.stream().filter(row->row[0].equals("C")&&row[2].equals(capture[1]))
                .mapToLong(row->Long.parseLong(row[1])).findFirst().getAsLong();
            long finalTime=Long.parseLong(events.get(events.size()-1)[1]);
            String[] lastPublication=events.get(events.size()-1);
            LocalPoint finalGoal=p(Integer.parseInt(lastPublication[2]),Integer.parseInt(lastPublication[3]));
            for(long cadence:new long[]{8_333,20_000,33_333})
            {
                Fixture f=new Fixture(Integer.parseInt(first[5]),Integer.parseInt(first[6]),Integer.parseInt(capture[2]),Integer.parseInt(capture[3]));
                try
                {
                    f.speed=1.16; f.catchUp=true; f.catchUpPercent=15; f.slowAhead=true; f.slowAheadPercent=10;
                    f.slowArrival=true; f.arrivalFinalSpeed=49.8; f.smoothing=0;
                    for(int[] column:f.flags) { Arrays.fill(column,CollisionDataFlag.BLOCK_MOVEMENT_FULL); }
                    for(String[] map:maps)
                    {
                        String[] values=map[2].split(",");
                        for(int y=0;y<values.length;++y) { f.flags[Integer.parseInt(map[1])][48+y]=Integer.parseUnsignedInt(values[y],16); }
                    }
                    TreeSet<Long> times=new TreeSet<>();
                    for(long t=0;t<=finalTime+2_000_000;t+=cadence) { times.add(t); }
                    for(String[] event:events) { times.add(Long.parseLong(event[1])); }
                    int next=0; long previousTime=0,held=0;
                    LocalPoint before=f.start;
                    for(long t:times)
                    {
                        f.now=1_000_000_000L+t*1000; f.cycle=100+(int)(t/20_000);
                        while(next<events.size()&&Long.parseLong(events.get(next)[1])<=t)
                        {
                            String[] event=events.get(next++);
                            if(event[0].equals("C"))
                            {
                                if(event[3].equals("WALK")) { f.controller.walkClick(); }
                                else
                                {
                                    MenuAction action=MenuAction.valueOf(event[3]);
                                    f.controller.worldInteraction(action.name().endsWith("_ON_NPC")||action.name().startsWith("NPC_")
                                        ?f.event(action,event[4]):f.sceneEvent(action,event[4],Integer.parseInt(event[11]),Integer.parseInt(event[9]),Integer.parseInt(event[10])));
                                }
                            }
                            else
                            {
                                f.authority=p(Integer.parseInt(event[2]),Integer.parseInt(event[3]));
                                f.destination=Integer.parseInt(event[4])<0?null:p(Integer.parseInt(event[4]),Integer.parseInt(event[5]));
                                f.nativePoint=p(Integer.parseInt(event[6]),Integer.parseInt(event[7])); f.animation=Integer.parseInt(event[8]);
                            }
                        }
                        int angle=f.controller.orientation(); f.controller.update();
                        LocalPoint actual=f.controller.position();
                        assertTrue("one clock "+capture[1],MovementPath.distance(before,actual)<=Math.ceil((t-previousTime)*0.000464*1.15)+1);
                        assertTrue("one turn cap",Math.abs(MotionMath.difference(angle,f.controller.orientation()))<=Math.ceil((t-previousTime)*30/16_667.0)+1);
                        if(t>clickTime+cadence&&t<finalTime && !actual.equals(finalGoal))
                        {
                            held=actual.equals(before)?held+t-previousTime:0;
                            assertTrue("instant click/continuous handoff "+capture[1]+" held="+held+" at "+actual,held<40_000);
                        }
                        before=actual; previousTime=t;
                    }
                    assertEquals("native final stopping point",f.authority,f.controller.position());
                }
                finally { f.controller.close(); assertTrue(f.objects.isEmpty()); }
            }
        }
    }

    @Test
    public void useNpcPlayerAndTradeHonorEveryDelayAndEarlyTickRelease()
    {
        for(MenuAction action:new MenuAction[]{MenuAction.ITEM_USE_ON_NPC,MenuAction.WIDGET_TARGET_ON_NPC,
            MenuAction.ITEM_USE_ON_PLAYER,MenuAction.WIDGET_TARGET_ON_PLAYER,MenuAction.PLAYER_FIFTH_OPTION})
        {
            for(int delay:new int[]{0,50,150,300})
            {
                for(boolean earlyTick:new boolean[]{false,true})
                {
                    Fixture f=new Fixture(6848,7488,6976,8256);
                    try
                    {
                        f.smoothing=delay;
                        f.controller.worldInteraction(f.event(action,action==MenuAction.PLAYER_FIFTH_OPTION?"Trade with":"Use"));
                        int release=earlyTick&&delay>40?40:delay;
                        for(int ms=0;ms<release;ms+=10)
                        {
                            f.frame(ms); assertEquals("wait for configured delay",f.start,f.controller.position());
                        }
                        f.at(release);
                        if(earlyTick) { f.controller.gameTick(); }
                        f.frame(release); f.frame(release+10);
                        assertNotEquals("eligible input responds as soon as delay/tick releases",f.start,f.controller.position());
                    }
                    finally { f.controller.close(); }
                }
            }
        }
    }

    @Test
    public void nativeSceneDestinationsAndNpcCombatShareTheDelayWithoutReobservingFlags()
    {
        for(int type=0;type<4;++type)
        {
            Fixture f=new Fixture(6848,7488,6976,8256);
            try
            {
                f.smoothing=150;
                if(type==0) { f.controller.worldInteraction(f.sceneEvent(MenuAction.GROUND_ITEM_THIRD_OPTION,"Take",233,54,64)); }
                if(type==1) { f.controller.worldInteraction(f.sceneEvent(MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,"Use",233,54,64)); }
                if(type==2) { f.click("Talk-to"); }
                if(type==3) { f.click("Attack"); }
                f.destination=p(6976,8128); f.frame(0); f.frame(100);
                assertEquals(f.start,f.controller.position());
                f.destination=p(7104,7488); // An older flag must not replace the first latched publication.
                f.frame(149); assertEquals(f.start,f.controller.position());
                f.frame(150); f.frame(170);
                assertNotEquals("same delay policy for all supported paths type="+type,f.start,f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void provisionalUseRetainsCollisionLifetimeAdjacentAndTargetChangeGuards()
    {
        for(int guard=0;guard<6;++guard)
        {
            Fixture f=new Fixture(6848,7488,6976,8256);
            try
            {
                if(guard==0) { f.control=true; }
                if(guard==1) { f.starts=false; }
                if(guard==2) { for(int[] column:f.flags) { Arrays.fill(column,CollisionDataFlag.BLOCK_MOVEMENT_FULL); } }
                if(guard==3) { f.area=new net.runelite.api.coords.WorldArea(3253,3258,1,1,0); }
                f.controller.worldInteraction(f.event(MenuAction.WIDGET_TARGET_ON_NPC,"Use"));
                f.frame(0); f.frame(20);
                if(guard<=3) { assertEquals("no unsupported start",f.start,f.controller.position()); }
                if(guard==4) { f.present=false; }
                if(guard==5) { f.area=new net.runelite.api.coords.WorldArea(3255,3264,1,1,0); }
                for(int ms=40;ms<=2800;ms+=20) { f.frame(ms); }
                assertEquals("bounded rejection/target recovery",f.authority,f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void shortUseSeedsStayMovingThroughLateAuthorityAtIdleAndBehindConfirmedDebt()
    {
        for(boolean player:new boolean[]{false,true})
        {
            for(boolean moving:new boolean[]{false,true})
            {
                for(double speed:new double[]{0.1,1,1.16,2})
                {
                    for(int cadence:new int[]{8,20,33,100})
                    {
                        Fixture f=new Fixture(6848,moving?7232:7488,6848,7744);
                        try
                        {
                            f.speed=speed;
                            if(moving) { f.authority=p(6848,7488); f.frame(20); }
                            f.at(40); f.controller.worldInteraction(f.event(player?MenuAction.WIDGET_TARGET_ON_PLAYER:MenuAction.WIDGET_TARGET_ON_NPC,"Use"));
                            f.frame(40);
                            LocalPoint before=f.controller.position();
                            for(int ms=40+cadence;ms<640;ms+=cadence)
                            {
                                f.frame(ms);
                                assertTrue("one configured budget",MovementPath.distance(before,f.controller.position())<=Math.ceil(cadence*0.4*speed)+1);
                                assertTrue("no invented tile",f.controller.position().getY()<=7616);
                                if(speed>=1&&cadence>=20)
                                {
                                    assertNotEquals("short seed must not finish early and publish idle",before,f.controller.position());
                                }
                                before=f.controller.position();
                            }
                            f.authority=p(6848,7616); f.destination=null;
                            for(int ms=640;ms<=25_000;ms+=100) { f.frame(ms); }
                            assertEquals(f.authority,f.controller.position());
                        }
                        finally { f.controller.close(); }
                    }
                }
            }
        }
    }

    @Test
    public void playerAttackAndDistantCastRespondWithTheirOwnConservativeBoundaries()
    {
        for(int type=0;type<3;++type)
        {
            Fixture f=new Fixture(6848,7488,6848,9152);
            try
            {
                f.smoothing=0;
                if(type==1) { f.ranged("Magic shortbow",861,1); }
                MenuAction action=type==2?MenuAction.WIDGET_TARGET_ON_PLAYER:MenuAction.PLAYER_FIRST_OPTION;
                f.controller.worldInteraction(f.event(action,type==2?"Cast":"Attack"));
                f.frame(0); f.frame(20);
                assertNotEquals("outside the conservative reserve, respond immediately",f.start,f.controller.position());
                for(int ms=40;ms<600;ms+=20)
                {
                    f.frame(ms);
                    assertTrue("range reserve does not assume adjacency",f.controller.position().getY()<=9152-(type==2?10:type==1?7:1)*128);
                }
                f.animation=711; f.poses.put("AnimationFrame",7);
                for(int ms=600;ms<=2600;ms+=20) { f.frame(ms); }
                assertEquals("no primary restart",Integer.valueOf(7),f.poses.get("AnimationFrame"));
                assertEquals("native action retires unconfirmed travel",f.authority,f.controller.position());
            }
            finally { f.controller.close(); }
        }
    }

    @Test
    public void aConfiguredDelayKeepsTheCurrentRunUntilTheNewInteractionIsReleased()
    {
        for(MenuAction action:new MenuAction[]{MenuAction.WIDGET_TARGET_ON_NPC,MenuAction.WIDGET_TARGET_ON_PLAYER})
        {
            Fixture f=new Fixture(6848,7488,6848,8256);
            try
            {
                f.smoothing=0; f.controller.walkClick(); f.destination=p(8128,7488);
                for(int ms=0;ms<=100;ms+=20) { f.frame(ms); }
                f.smoothing=150; f.controller.worldInteraction(f.event(action,"Use")); f.destination=null;
                LocalPoint before=f.controller.position();
                for(int ms=120;ms<250;ms+=20)
                {
                    f.frame(ms);
                    assertTrue("the configured wait must not stop the existing run",f.controller.position().getX()>before.getX());
                    assertEquals("do not redirect before release",7488,f.controller.position().getY());
                    before=f.controller.position();
                }
                f.frame(250);
                before=f.controller.position();
                boolean turned=false;
                for(int ms=270;ms<=700;ms+=20)
                {
                    f.frame(ms); turned|=f.controller.position().getY()>7488;
                    assertTrue("a single movement budget across release",MovementPath.distance(before,f.controller.position())<=12);
                    before=f.controller.position();
                }
                assertTrue("checked Use approach begins after release",turned);
            }
            finally { f.controller.close(); }
        }
    }
}
