package top.hibermod.ctdhost.topology;

import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;

import top.hibermod.ctdapi.Rail;

/**
 * Create 的图对象 ↔ API 的稳定 id。
 *
 * <h2>为什么是 1:1 映射，没有量化运算</h2>
 * {@code TrackNodeLocation} 继承 {@code Vec3i}，它的 {@code getX()/getY()/getZ()}
 * **本来就是半格整数**（`TrackNodeLocation.java:23`），`yOffsetPixels` 是 1/16 格偏移。
 * 这正好对上 {@link Rail.NodeId} 的 `x2/y2/z2` 与 `yOffsetPixels`——所以转换是恒等的，
 * 不需要任何取整或缩放。曾经担心要做 {@code floor(round(x*2))}，看完源码发现不用。
 */
public final class StableIds {

    private StableIds() {}

    public static Rail.NodeId node(TrackNodeLocation loc) {
        return new Rail.NodeId(
            loc.getDimension(),
            loc.getX(),
            loc.getY(),
            loc.getZ(),
            loc.yOffsetPixels);
    }

    public static Rail.NodeId node(TrackNode node) {
        return node(node.getLocation());
    }

    public static Rail.EdgeId edge(TrackNode a, TrackNode b) {
        return Rail.EdgeId.of(node(a), node(b));
    }
}
