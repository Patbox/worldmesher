package net.fabricmc.fabric.impl.client.indigo.renderer.render;

import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoLuminanceFix;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.world.BlockRenderView;

import java.util.function.Consumer;
import java.util.function.Function;

public class WorldMesherRenderContext extends AbstractRenderContext {

    private final TerrainBlockRenderInfo blockInfo;
    private final AoCalculator aoCalc;

    private final AbstractMeshConsumer meshConsumer;
    private final TerrainFallbackConsumer fallbackConsumer;

    public WorldMesherRenderContext(BlockRenderView blockView, Function<RenderLayer, VertexConsumer> bufferFunc) {
        this.blockInfo = new TerrainBlockRenderInfo();
        this.blockInfo.setBlockView(blockView);

        this.aoCalc = new AoCalculator(
                blockInfo,
                (pos, state) -> WorldRenderer.getLightmapCoordinates(blockView, state, pos),
                (pos, state) -> AoLuminanceFix.INSTANCE.apply(blockView, pos, state)
        );

        this.meshConsumer = new AbstractMeshConsumer(blockInfo, bufferFunc, aoCalc, this::transform) {
            @Override
            protected int overlay() {
                return overlay;
            }

            @Override
            protected Matrix4f matrix() {
                return matrix;
            }

            @Override
            protected Matrix3f normalMatrix() {
                return normalMatrix;
            }
        };

        this.fallbackConsumer = new TerrainFallbackConsumer(blockInfo, bufferFunc, aoCalc, this::transform) {
            @Override
            protected int overlay() {
                return overlay;
            }

            @Override
            protected Matrix4f matrix() {
                return matrix;
            }

            @Override
            protected Matrix3f normalMatrix() {
                return normalMatrix;
            }
        };
    }

    public void tessellateBlock(BlockRenderView blockView, BlockState blockState, BlockPos blockPos, final BakedModel model, MatrixStack matrixStack) {
        this.matrix = matrixStack.peek().getPositionMatrix();
        this.normalMatrix = matrixStack.peek().getNormalMatrix();

        try {
            aoCalc.clear();
            blockInfo.prepareForBlock(blockState, blockPos, model.useAmbientOcclusion());
            ((FabricBakedModel) model).emitBlockQuads(blockInfo.blockView, blockInfo.blockState, blockInfo.blockPos, blockInfo.randomSupplier, this);
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.create(throwable, "Tessellating block in WorldMesher mesh");
            CrashReportSection crashReportSection = crashReport.addElement("Block being tessellated");
            CrashReportSection.addBlockInfo(crashReportSection, blockView, blockPos, blockState);
            throw new CrashException(crashReport);
        }
    }

    @Override
    public Consumer<Mesh> meshConsumer() {
        return this.meshConsumer;
    }

    @Override
    public Consumer<BakedModel> fallbackConsumer() {
        return this.fallbackConsumer;
    }

    @Override
    public QuadEmitter getEmitter() {
        return this.meshConsumer.getEmitter();
    }
}
