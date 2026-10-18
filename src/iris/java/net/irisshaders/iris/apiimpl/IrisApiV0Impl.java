package net.irisshaders.iris.apiimpl;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisApiConfig;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.api.v0.IrisShadowProgram;
import net.irisshaders.iris.api.v0.IrisShadowRenderCallback;
import net.irisshaders.iris.api.v0.IrisTextVertexSink;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shadows.ShadowRenderCallbacks;
import net.minecraft.client.gui.screen.Screen;
import yt.ren.astatine.iris.IrisPlatform;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

public class IrisApiV0Impl implements IrisApi {
	public static final IrisApiV0Impl INSTANCE = new IrisApiV0Impl();
	private static final IrisApiV0ConfigImpl CONFIG = new IrisApiV0ConfigImpl();

	@Override
	public int getMinorApiRevision() {
		return 4;
	}

	@Override
	public boolean isShaderPackInUse() {
		return IrisPlatform.get().isActive();
	}

	@Override
	public boolean isRenderingShadowPass() {
		return IrisPlatform.get().isRenderingShadows();
	}

	@Override
	public Object openMainIrisScreenObj(Object parent) {
		return new ShaderPackScreen((Screen) parent);
	}

	@Override
	public String getMainScreenLanguageKey() {
		return "options.iris.shaderPackSelection";
	}

	@Override
	public IrisApiConfig getConfig() {
		return CONFIG;
	}

	@Override
	public IrisTextVertexSink createTextVertexSink(int maxQuadCount, IntFunction<ByteBuffer> bufferProvider) {
		return null;
	}

	@Override
	public float getSunPathRotation() {
		return IrisPlatform.get().sunPathRotation();
	}

	@Override
	public void assignPipeline(Object pipeline, IrisProgram program) {
	}

	@Override
	public void assignPipelineShadow(Object pipeline, IrisShadowProgram program) {
	}

	@Override
	public void registerShadowRenderCallback(IrisShadowRenderCallback callback) {
		ShadowRenderCallbacks.register(callback);
	}

	@Override
	public boolean isReverseZDuringShaders() {
		return false;
	}
}
