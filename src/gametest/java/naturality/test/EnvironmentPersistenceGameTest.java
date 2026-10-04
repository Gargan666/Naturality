package naturality.test;

import java.util.*;
import naturality.weather.*;
import naturality.sky.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerLevel;

public final class EnvironmentPersistenceGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    private static void invoke(Class<?> owner,String method,Class<?>[] types,Object... args) {
        try { var m=owner.getDeclaredMethod(method,types);m.setAccessible(true);m.invoke(null,args); }
        catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Map<String,Long> cycle() {
        var cycle=new SkyEventCycle(731);
        for(int i=0;i<200000 && !cycle.active();i++)cycle.tick(true);
        for(int i=0;i<250;i++)cycle.tick(true);
        check(cycle.active() && cycle.strength()>0,"Fixture has an automatic sky event");
        var saved=cycle.snapshot();
        var restored=new SkyEventCycle(1);restored.restore(cycle.snapshot());
        for(int i=0;i<40000;i++) { cycle.tick(true);restored.tick(true);check(cycle.snapshot().equals(restored.snapshot()),"Sky random sequence resumes exactly"); }
        return saved;
    }
    @Override public void runTest(ClientGameTestContext context) {
        var meteor=cycle();
        var rainbow=new RainbowCycle(91);
        for(int i=0;i<200000 && !rainbow.active();i++)rainbow.tick(true,true,10);
        for(int i=0;i<200;i++)rainbow.tick(true,true,10);
        var savedRainbow=rainbow.snapshot();
        var rainbowCopy=new RainbowCycle(2);rainbowCopy.restore(rainbow.snapshot());
        for(int i=0;i<20000;i++) { rainbow.tick(true,true,10);rainbowCopy.tick(true,true,10);check(rainbow.snapshot().equals(rainbowCopy.snapshot()),"Rainbow resumes exactly"); }
        var aurora=new AuroraCycle(123);for(int i=0;i<800;i++)aurora.tick(true,1);
        var savedAurora=aurora.snapshot();
        var auroraCopy=new AuroraCycle(2);auroraCopy.restore(aurora.snapshot());
        for(int i=0;i<20000;i++) { aurora.tick(true,1);auroraCopy.tick(true,1);check(aurora.snapshot().equals(auroraCopy.snapshot()),"Aurora resumes exactly"); }
        net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave save;
        var expected=new HashMap<String,Map<String,Long>>();
        try(var world=context.worldBuilder().create()) {
            save=world.getWorldSave();
            world.getServer().runCommand("gamerule minecraft:advance_weather false");
            world.getServer().runOnServer(server -> {
                var data=EnvironmentWorldData.get(server);
                int index=0;
                for(var level:server.getAllLevels()) {
                    String dimension=level.dimension().identifier().toString();
                    WeatherWorldData.get(server).setProfile(dimension,new WeatherProfile(true));
                    String key="weather/"+dimension;
                    var state=Map.of("rain",EnvironmentWorldData.bits(10+index*20),"wind",EnvironmentWorldData.bits(45+index),
                        "temperature",EnvironmentWorldData.bits(23+index),"direction",EnvironmentWorldData.bits(317+index),"clock",123456L+index);
                    data.write(key,state);
                    for(String channel:List.of("rain","wind"))data.write(key+"/"+channel,Map.of("random",927123L,"active",1L,"remaining",3210L,"age",720L,
                        "value",state.get(channel),"boost",EnvironmentWorldData.bits(25)));
                    invoke(WeatherSystem.class,"restore",new Class<?>[]{ServerLevel.class},level);
                    index++;
                    if(SkyEventType.pool(dimension).contains(SkyEventType.METEOR_SHOWER)) {
                        try {
                            var method=SkyEvents.class.getDeclaredMethod("cycle",ServerLevel.class,SkyEventType.class);method.setAccessible(true);
                            ((SkyEventCycle)method.invoke(null,level,SkyEventType.METEOR_SHOWER)).restore(meteor);
                        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
                    }
                    if(SkyEventType.pool(dimension).contains(SkyEventType.RAINBOW)) {
                        try {
                            var method=SkyEvents.class.getDeclaredMethod("rainbowCycle",ServerLevel.class);method.setAccessible(true);
                            ((RainbowCycle)method.invoke(null,level)).restore(savedRainbow);
                        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
                    }
                }
                var player=server.getPlayerList().getPlayers().getFirst();
                int x=Math.floorDiv(player.blockPosition().getX(),1024),z=Math.floorDiv(player.blockPosition().getZ(),1024);
                var local=new HashMap<>(savedAurora);
                local.put("x",(long)x);local.put("z",(long)z);local.put("lastSeen",server.overworld().getGameTime());
                data.write("aurora/minecraft:overworld/"+x+","+z,local);
                AuroraEvents.restore(server.overworld());
            });
            context.waitTicks(5);
            world.getServer().runOnServer(server -> EnvironmentWorldData.get(server).entries().forEach((key,value) -> {
                if(!key.startsWith("aurora/"))expected.put(key,value);
            }));
        }
        try(var other=context.worldBuilder().create()) {
            other.getServer().runOnServer(server -> check(WeatherSystem.state(server.overworld()).rain()==0 && WeatherSystem.state(server.overworld()).wind()==0,
                "A different world starts with calm weather"));
        }
        try(var reopened=save.open()) {
            reopened.getConnection().waitForClientboundPackets();context.waitTicks(5);
            reopened.getServer().runOnServer(server -> {
                var data=EnvironmentWorldData.get(server);
                expected.forEach((key,value) -> check(data.read(key).equals(value),"Runtime snapshot survives real world reopen: "+key));
                for(var level:server.getAllLevels()) {
                    var state=WeatherSystem.state(level);
                    var saved=expected.get("weather/"+level.dimension().identifier());
                    check(state!=null && state.rain()==EnvironmentWorldData.number(saved,"rain") && state.wind()==EnvironmentWorldData.number(saved,"wind")
                        && state.temperature()==EnvironmentWorldData.number(saved,"temperature") && state.direction()==EnvironmentWorldData.number(saved,"direction"),
                        "Each dimension restores live weather, not just saved metadata");
                    if(SkyEventType.pool(level.dimension().identifier().toString()).contains(SkyEventType.METEOR_SHOWER))
                        check(SkyEvents.strength(level,SkyEventType.METEOR_SHOWER)==EnvironmentWorldData.number(meteor,"strength"),"Active meteor strength survives reopen");
                }
                var player=server.getPlayerList().getPlayers().getFirst();
                check(AuroraEvents.strength(server.overworld(),player.blockPosition())==EnvironmentWorldData.number(savedAurora,"strength"),"Local aurora survives reopen");
            });
            context.runOnClient(client -> check(WeatherSystem.state(client.level).rain()==EnvironmentWorldData.number(expected.get("weather/minecraft:overworld"),"rain"),
                "Joining client receives restored weather"));
        }
    }
}
