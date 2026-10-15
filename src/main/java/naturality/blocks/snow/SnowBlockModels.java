package naturality.blocks.snow;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;

/** Model-derived upper surfaces shared by clients and dedicated servers. */
@org.jspecify.annotations.NullMarked
public final class SnowBlockModels {
    private static final JsonObject DATA=read("data/naturality/snow_models.json");
    private static final Map<String,JsonObject> RESOURCES=new ConcurrentHashMap<>();
    private static final Map<BlockState,List<AABB>> GEOMETRY=new ConcurrentHashMap<>();
    private static final Map<BlockState,Boolean> FULL_TOP=new ConcurrentHashMap<>(),EXPOSED_GROUND=new ConcurrentHashMap<>();
    private SnowBlockModels() { }

    public static List<AABB> boxes(BlockState state) {
        return GEOMETRY.computeIfAbsent(state,SnowBlockModels::bake);
    }
    public static boolean fullTop(BlockState state) {
        return FULL_TOP.computeIfAbsent(state,key -> {
            var top=net.minecraft.world.phys.shapes.Shapes.empty();
            for(var box:boxes(key))if(Math.abs(box.maxY-1)<1e-5)
                top=net.minecraft.world.phys.shapes.Shapes.or(top,net.minecraft.world.phys.shapes.Shapes.box(box.minX,0,box.minZ,box.maxX,1,box.maxZ));
            return net.minecraft.world.level.block.Block.isShapeFullBlock(top);
        });
    }
    public static boolean exposesGround(BlockState state) {
        return EXPOSED_GROUND.computeIfAbsent(state,key -> {
            var footprint=net.minecraft.world.phys.shapes.Shapes.empty();
            for(var box:boxes(key))footprint=net.minecraft.world.phys.shapes.Shapes.or(footprint,
                net.minecraft.world.phys.shapes.Shapes.box(box.minX,0,box.minZ,box.maxX,1,box.maxZ));
            return net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(net.minecraft.world.phys.shapes.Shapes.block(),footprint,
                net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
        });
    }
    private static List<AABB> bake(BlockState state) {
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        var definition=resource("blockstates",id);
        var result=new ArrayList<AABB>();
        if(definition.has("variants"))for(var variant:definition.getAsJsonObject("variants").entrySet()) {
            boolean matches=true;
            for(String part:variant.getKey().split(","))if(!part.isEmpty()) {
                String[] pair=part.split("=",2);
                if(pair.length!=2 || !matches(state,pair[0],pair[1])) { matches=false;break; }
            }
            if(matches)apply(variant.getValue(),result);
        }
        if(definition.has("multipart"))for(var part:definition.getAsJsonArray("multipart")) {
            var entry=part.getAsJsonObject();
            if(!entry.has("when") || condition(state,entry.getAsJsonObject("when")))apply(entry.get("apply"),result);
        }
        if(result.isEmpty())result.addAll(SnowBuiltinModels.boxes(state));
        var shape=net.minecraft.world.phys.shapes.Shapes.empty();
        for(var box:result)shape=net.minecraft.world.phys.shapes.Shapes.or(shape,net.minecraft.world.phys.shapes.Shapes.create(box));
        return List.copyOf(shape.optimize().toAabbs());
    }
    private static boolean condition(BlockState state,JsonObject condition) {
        for(var entry:condition.entrySet()) {
            if(entry.getKey().equals("OR") || entry.getKey().equals("AND")) {
                boolean and=entry.getKey().equals("AND"),value=and;
                for(var child:entry.getValue().getAsJsonArray()) {
                    boolean match=condition(state,child.getAsJsonObject());
                    value=and?value&&match:value||match;
                }
                if(!value)return false;
            } else if(!matches(state,entry.getKey(),entry.getValue().getAsString()))return false;
        }
        return true;
    }
    private static boolean matches(BlockState state,String name,String alternatives) {
        var property=state.getBlock().getStateDefinition().getProperty(name);
        if(property==null)return false;
        return Arrays.asList(alternatives.split("\\|")).contains(value(state,property));
    }
    private static <T extends Comparable<T>> String value(BlockState state,Property<T> property) {
        return property.getName(state.getValue(property));
    }
    private static void apply(JsonElement application,List<AABB> result) {
        // Vanilla's weighted alternatives share their support envelope. A stable
        // choice keeps client/server placement and collision in agreement.
        if(application.isJsonArray())application=application.getAsJsonArray().get(0);
        var entry=application.getAsJsonObject();
        var elements=elements(entry.get("model").getAsString(),new HashSet<>());
        for(var element:elements) {
            var cube=element.getAsJsonObject();
            if(!cube.has("faces"))continue;
            double[] from=vector(cube.getAsJsonArray("from")),to=vector(cube.getAsJsonArray("to"));
            for(var face:cube.getAsJsonObject("faces").keySet()) {
                double[][] points=switch(face) {
                    case "up" -> new double[][]{{from[0],to[1],from[2]},{to[0],to[1],from[2]},{to[0],to[1],to[2]},{from[0],to[1],to[2]}};
                    case "down" -> new double[][]{{from[0],from[1],from[2]},{from[0],from[1],to[2]},{to[0],from[1],to[2]},{to[0],from[1],from[2]}};
                    case "north" -> new double[][]{{from[0],from[1],from[2]},{to[0],from[1],from[2]},{to[0],to[1],from[2]},{from[0],to[1],from[2]}};
                    case "south" -> new double[][]{{to[0],from[1],to[2]},{from[0],from[1],to[2]},{from[0],to[1],to[2]},{to[0],to[1],to[2]}};
                    case "west" -> new double[][]{{from[0],from[1],to[2]},{from[0],from[1],from[2]},{from[0],to[1],from[2]},{from[0],to[1],to[2]}};
                    case "east" -> new double[][]{{to[0],from[1],from[2]},{to[0],from[1],to[2]},{to[0],to[1],to[2]},{to[0],to[1],from[2]}};
                    default -> new double[0][];
                };
                for(var point:points) {
                    if(cube.has("rotation")) {
                        var rotation=cube.getAsJsonObject("rotation");
                        rotate(point,vector(rotation.getAsJsonArray("origin")),rotation.get("axis").getAsString(),
                            rotation.get("angle").getAsDouble(),rotation.has("rescale")&&rotation.get("rescale").getAsBoolean());
                    }
                    rotate(point,new double[]{.5,.5,.5},"x",-number(entry,"x"),false);
                    rotate(point,new double[]{.5,.5,.5},"y",-number(entry,"y"),false);
                }
                if(points.length==4)surface(points,result);
            }
        }
    }
    private static double number(JsonObject object,String key) { return object.has(key)?object.get(key).getAsDouble():0; }
    private static JsonArray elements(String id,Set<String> visited) {
        id=qualified(id);
        if(!visited.add(id))return new JsonArray();
        var model=resource("models",id);
        if(model.has("elements"))return model.getAsJsonArray("elements");
        return model.has("parent")?elements(model.get("parent").getAsString(),visited):new JsonArray();
    }
    private static String qualified(String id) { return id.contains(":")?id:"minecraft:"+id; }
    private static JsonObject resource(String kind,String id) {
        id=qualified(id);
        var bundled=DATA.getAsJsonObject(kind);
        if(bundled!=null && bundled.has(id))return bundled.getAsJsonObject(id);
        String[] parts=id.split(":",2);
        return RESOURCES.computeIfAbsent(kind+":"+id,_->read("assets/"+parts[0]+"/"+kind+"/"+parts[1]+".json"));
    }
    private static JsonObject read(String path) {
        try(var stream=SnowBlockModels.class.getClassLoader().getResourceAsStream(path)) {
            if(stream==null)return new JsonObject();
            try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        } catch(java.io.IOException | JsonParseException e) { throw new IllegalStateException("Cannot read snow model "+path,e); }
    }
    private static double[] vector(JsonArray array) {
        return new double[]{array.get(0).getAsDouble()/16,array.get(1).getAsDouble()/16,array.get(2).getAsDouble()/16};
    }
    private static void rotate(double[] point,double[] origin,String axis,double angle,boolean rescale) {
        if(angle==0)return;
        int a=axis.equals("x")?1:axis.equals("y")?2:0,b=(a+1)%3;
        double radians=Math.toRadians(angle),c=Math.cos(radians),s=Math.sin(radians);
        double x=point[a]-origin[a],y=point[b]-origin[b],scale=rescale?1/Math.abs(c):1;
        point[a]=origin[a]+(x*c-y*s)*scale;point[b]=origin[b]+(x*s+y*c)*scale;
    }
    private static void surface(double[][] p,List<AABB> result) {
        double ax=p[1][0]-p[0][0],ay=p[1][1]-p[0][1],az=p[1][2]-p[0][2];
        double bx=p[3][0]-p[0][0],by=p[3][1]-p[0][1],bz=p[3][2]-p[0][2];
        // Face winding above has the cross product pointing inward.
        double nx=az*by-ay*bz,ny=ax*bz-az*bx,nz=ay*bx-ax*by;
        if(ny<=1e-8)return;
        double x=Double.POSITIVE_INFINITY,z=x,X=Double.NEGATIVE_INFINITY,Z=X;
        for(var point:p) { x=Math.min(x,point[0]);X=Math.max(X,point[0]);z=Math.min(z,point[2]);Z=Math.max(Z,point[2]); }
        boolean rectangle=Math.abs(ay)<1e-7&&Math.abs(by)<1e-7
            && (Math.abs(ax)<1e-7||Math.abs(az)<1e-7)&&(Math.abs(bx)<1e-7||Math.abs(bz)<1e-7);
        if(rectangle) { if(X>x+1e-7&&Z>z+1e-7)result.add(new AABB(x,p[0][1]-1e-6,z,X,p[0][1],Z));return; }
        // Rotated and sloped faces are rasterized on the block pixel grid.
        for(int ix=(int)Math.floor(x*16);ix<Math.ceil(X*16);ix++)for(int iz=(int)Math.floor(z*16);iz<Math.ceil(Z*16);iz++) {
            double px=(ix+.5)/16,pz=(iz+.5)/16;
            double det=ax*bz-az*bx;
            double u=((px-p[0][0])*bz-(pz-p[0][2])*bx)/det,v=(ax*(pz-p[0][2])-az*(px-p[0][0]))/det;
            if(u<0||u>1||v<0||v>1)continue;
            double y=p[0][1]-(nx*(px-p[0][0])+nz*(pz-p[0][2]))/ny;
            result.add(new AABB(ix/16.0,y-1e-6,iz/16.0,(ix+1)/16.0,y,(iz+1)/16.0));
        }
    }

    /** Rendered model surfaces, independent of selection and collision hitboxes. */
    public static final class SnowSupportModels {
        private SnowSupportModels() { }

        public static List<AABB> boxes(BlockGetter level, BlockPos pos) {
            return SnowBlockModels.boxes(level.getBlockState(pos));
        }
    }

    /** Upper surfaces of block-entity meshes which have no JSON model elements. */
    static final class SnowBuiltinModels {
        private SnowBuiltinModels() { }
        static List<AABB> boxes(BlockState state) {
            var block=state.getBlock();
            if(block instanceof ChestBlock || block instanceof EnderChestBlock) {
                double x=1,z=1,X=15,Z=15;
                if(block instanceof ChestBlock) {
                    var type=state.getValue(ChestBlock.TYPE);
                    if(type!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                        var joined=ChestBlock.getConnectedDirection(state);
                        switch(joined) {
                            case NORTH -> z=0;
                            case SOUTH -> Z=16;
                            case WEST -> x=0;
                            case EAST -> X=16;
                            default -> { }
                        }
                    }
                }
                return List.of(top(x,14,z,X,Z));
            }
            if(block instanceof ShulkerBoxBlock)return List.of(top(0,16,0,16,16));
            if(block instanceof DecoratedPotBlock)return List.of(top(1,16,1,15,15));
            if(block instanceof ConduitBlock)return List.of(top(5,11,5,11,11));
            if(block instanceof AbstractSkullBlock) {
                if(block instanceof WallSkullBlock) {
                    var facing=state.getValue(WallSkullBlock.FACING);
                    return switch(facing) {
                        case NORTH -> List.of(top(4,12,8,12,16));
                        case SOUTH -> List.of(top(4,12,0,12,8));
                        case EAST -> List.of(top(0,12,4,8,12));
                        case WEST -> List.of(top(8,12,4,16,12));
                        default -> List.of();
                    };
                }
                return List.of(top(4,8,4,12,12));
            }
            if(block instanceof BedBlock)return List.of(top(0,9,0,16,16));
            // Invisible blocks and temporary moving-piston cells have no static
            // rendered upper face and therefore do not produce a floating cap.
            return List.of();
        }
        private static AABB top(double x,double y,double z,double X,double Z) {
            return new AABB(x/16,y/16-1e-6,z/16,X/16,y/16,Z/16);
        }
    }
}
