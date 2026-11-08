package com.pushupanalyzer.app

import android.graphics.Bitmap
import android.opengl.*
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** An EGL window surface with explicit media timestamps; Canvas never touches the codec surface. */
class EncoderSurface(surface: Surface) : AutoCloseable {
    private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private var context = EGL14.EGL_NO_CONTEXT
    private var window = EGL14.EGL_NO_SURFACE
    private var program = 0
    private val texture = IntArray(1)
    private var allocated = false
    private val quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,1f,1f, -1f,1f,0f,0f, 1f,1f,1f,0f)); position(0)
    }
    init {
        try {
            check(display != EGL14.EGL_NO_DISPLAY)
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 1))
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RED_SIZE,8,
                EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,0x3142,1,EGL14.EGL_NONE),
                0, configs, 0, 1, count, 0) && count[0] > 0)
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            check(context != EGL14.EGL_NO_CONTEXT)
            window = EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE),0)
            check(window != EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display,window,window,context))
            val vs = shader(GLES20.GL_VERTEX_SHADER,
                "attribute vec2 p; attribute vec2 uv; varying vec2 v; void main(){gl_Position=vec4(p,0.,1.);v=uv;}")
            val fs = shader(GLES20.GL_FRAGMENT_SHADER,
                "precision mediump float; varying vec2 v; uniform sampler2D image; void main(){gl_FragColor=texture2D(image,v);}")
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program,vs); GLES20.glAttachShader(program,fs)
            GLES20.glLinkProgram(program)
            GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
            val linked = IntArray(1); GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0)
            check(linked[0] != 0) { GLES20.glGetProgramInfoLog(program) }
            GLES20.glGenTextures(1,texture,0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        } catch (t: Throwable) { close(); throw t }
    }
    private fun shader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id,source); GLES20.glCompileShader(id)
        val compiled = IntArray(1); GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,compiled,0)
        if (compiled[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(id); GLES20.glDeleteShader(id); error(message)
        }
        return id
    }
    fun draw(bitmap: Bitmap, presentationTimeUs: Long) {
        GLES20.glViewport(0,0,bitmap.width,bitmap.height)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture[0])
        if (!allocated) { GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0); allocated = true }
        else GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,bitmap)
        val p = GLES20.glGetAttribLocation(program,"p")
        val uv = GLES20.glGetAttribLocation(program,"uv")
        quad.position(0); GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,16,quad)
        quad.position(2); GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,quad)
        GLES20.glEnableVertexAttribArray(p); GLES20.glEnableVertexAttribArray(uv)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "OpenGL frame upload failed" }
        check(EGLExt.eglPresentationTimeANDROID(display,window,presentationTimeUs * 1000))
        check(EGL14.eglSwapBuffers(display,window)) { "Encoder surface rejected frame" }
    }
    override fun close() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (context != EGL14.EGL_NO_CONTEXT) {
                GLES20.glDeleteTextures(1,texture,0)
                if (program != 0) GLES20.glDeleteProgram(program)
            }
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,window)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,context)
            EGL14.eglReleaseThread(); EGL14.eglTerminate(display)
        }
    }
}
