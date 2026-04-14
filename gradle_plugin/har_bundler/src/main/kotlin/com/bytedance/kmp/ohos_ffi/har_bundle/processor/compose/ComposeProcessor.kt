/*
 * Copyright (c) 2026 ByteDance Ltd. and/or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bytedance.kmp.ohos_ffi.har_bundle.processor.compose

import com.bytedance.kmp.ohos_ffi.har_bundle.BundleHarConfig
import com.bytedance.kmp.ohos_ffi.har_bundle.HarExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.OhosFfiExtension
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.ISoHarGeneratorProcessor
import com.bytedance.kmp.ohos_ffi.har_bundle.processor.basic.DefineMethodInfo
import com.bytedance.kmp.ohos_ffi.har_bundle.utils.rewrite
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

class ComposeProcessor : ISoHarGeneratorProcessor {

    companion object {
        const val META_INFO_PATH = "generated/ksp/ohosArm64/ohosArm64Main/kotlin/ohos_compose_meta_info.kt"
    }

    private lateinit var project: Project

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun onTaskCreate(soGenerateTask: Task) {
        this.project = soGenerateTask.project
    }

    override fun process(templateProjectDir: File, extension: BundleHarConfig) {
        if (!extension.enableCompose) {
            return
        }
        val composeDir = createDirIfNeed(templateProjectDir, extension)
        writeComposeViewCode(composeDir, extension)
        writeExportComposeViews(composeDir, extension, getIndexFile(templateProjectDir, extension))
    }

    private fun getIndexFile(templateProjectDir: File, extension: BundleHarConfig): File {
        return File(templateProjectDir, "${extension.soName}/Index.ets")
    }

    private fun createDirIfNeed(templateProjectDir: File, extension: BundleHarConfig): File {
        val moduleFile = File(templateProjectDir, extension.soName)
        val etsDir = File(moduleFile, "src/main/ets").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        return File(etsDir, "compose").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * 通用的 ComposeView 代码
     */
    private fun writeComposeViewCode(composeDir: File, extension: BundleHarConfig) {
        val file = File(composeDir, "ComposeView.ets").apply {
            if (exists()) {
                delete()
            }
            createNewFile()
        }
        file.writer().use {
            it.write("""
                import { setResourceManager, onDensityPixelsChanged, onKeyboardHeightChanged, onFontSizeScaleChanged, onFontWeightScaleChanged, onSystemFontIdChanged, onLanguageChanged, onWindowInsetsChanged, WindowInsetsRect, ShellRenderView, ${if (extension.enablePerformanceProbe) "initRenderNodeWithPreloadProbe" else "initRenderNode"}, on24HourFormatChanged, setNodeConstructor, onJsNodeDraw, ShellFrameImportApi, ShellFrameExportApi, ShellSizeConstraint, TextToolBarCallback, ImportScrollable, ImportOnTouchListener${if (extension.enablePerformanceProbe) ", PreloadProbe" else ""}, setCApiFixed } from 'lib${extension.soName}.so'
                import { display, window, NodeContent, DrawContext, FrameNode, NodeController, RenderNode, UIContext, ShapeClip, Rect, Matrix4, Position, FrameCallback, RoundRect, BuilderNode } from '@kit.ArkUI';
                import { common } from '@kit.AbilityKit';
                import { displaySync } from '@kit.ArkGraphics2D';
                import { hilog, hiTraceMeter } from '@kit.PerformanceAnalysisKit';
                import I18n from '@ohos.i18n';
                import { BusinessError, pasteboard } from '@kit.BasicServicesKit';
                import { webview } from '@kit.ArkWeb';
                import { deviceInfo, systemDateTime } from '@kit.BasicServicesKit';
                
                export const renderNodeCApiSupported: boolean = (deviceInfo.distributionOSApiVersion > 60000 || (deviceInfo.distributionOSApiVersion == 60000 && deviceInfo.buildVersion >= 45))
                const renderNodeCApiFixed: boolean = (deviceInfo.distributionOSApiVersion > 60000 || (deviceInfo.distributionOSApiVersion == 60000 && deviceInfo.buildVersion >= 107))
                let isRenderNodeCApiDefaultEnable: boolean = false
                export function setRenderNodeCApiDefaultEnable() {
                  isRenderNodeCApiDefaultEnable = true
                }
                
                ${if (extension.enablePerformanceProbe) {
                """
                    export class PreloadHandler implements PreloadProbe {
                        private preloadedTime = -1
                        private uuid: string
                        
                        constructor(uuid: string) {
                            this.uuid = uuid
                        }
                    
                        isActualLaunched(): number {
                            return this.preloadedTime
                        }
                        
                        markPreloaded() {
                            this.preloadedTime = systemDateTime.getTime()
                        }
                        
                        getUuid(): string {
                            return this.uuid
                        }
                    }
                    
                    export function DefaultPreloadProbe(): PreloadHandler {
                        return new PreloadHandler("")
                    }
                    """.trimIndent()
            } else ""}
                
                export const SIZE_CONSTRAINT_MATCH_PARENT = -1
                export const SIZE_CONSTRAINT_WRAP_CONTENT = -2
                
                /*
                 * desc: 尺寸约束接口
                 */
                export class HarkoSizeConstraint implements ShellSizeConstraint {
                  private widthConstraint: number
                  private heightConstraint: number

                  constructor(widthConstraint: number, heightConstraint: number) {
                    this.widthConstraint = widthConstraint;
                    this.heightConstraint = heightConstraint;
                  }

                  width(): number {
                    return this.widthConstraint
                  }

                  height(): number {
                    return this.heightConstraint
                  }
                  
                  widthForLength(): Length {
                    return this.toLength(this.width())
                  }
                
                  heightForLength(): Length {
                    return this.toLength(this.height())
                  }
                
                  private toLength(size: number): Length {
                    switch (size) {
                      case SIZE_CONSTRAINT_WRAP_CONTENT:
                        return 'auto'
                      case SIZE_CONSTRAINT_MATCH_PARENT:
                        return '100%'
                      default:
                        return 'auto'
                    }
                  }
                }
                
                /*
                 * desc: 销毁事件接口
                 */
                export interface OnDisposeCallback {
                  onDispose(): void
                }
                
                /*
                 * desc: 销毁事件集合
                 */
                export class OnDisposeCollection implements OnDisposeCallback {
                  private callbacks: OnDisposeCallback[] = []

                  addCallback(callback: OnDisposeCallback) {
                    this.callbacks.push(callback)
                  }

                  onDispose(): void {
                    for (const callback of this.callbacks) {
                      callback.onDispose()
                    }
                    this.callbacks.length = 0
                  }
                }
                
                /*
                 * desc: 返回事件接口
                 */
                export interface OnBackPressCallback {
                  onBackPress(): boolean
                }
                
                /*
                 * desc: 返回事件总线
                 */
                export class OnBackPressEventBus implements OnBackPressCallback, OnDisposeCallback {
                  private callbacks: OnBackPressCallback[] = []

                  addCallback(callback: OnBackPressCallback) {
                    this.callbacks.push(callback)
                  }

                  onBackPress(): boolean {
                    for (const callback of this.callbacks) {
                      if (callback.onBackPress()) {
                        return true
                      }
                    }
                    return false
                  }
                  
                  onDispose() {
                    this.callbacks.length = 0
                  }
                }
                
                /*
                 * desc: 初始化节点构造器
                 */
                export function initRenderNodeContext(): void {
                  setNodeConstructor(HarkoChildRenderNode, NodeStatusModify)
                }
                
                /*
                 * desc: 抽象节点接口
                 */
                export interface AbsHarkoRenderNode extends OnBackPressCallback {
                  dispose(): void

                  resize(size: SizeResult, posChanged: boolean): void

                  resetSize(size: SizeResult): void
                  
                  onTouchIntercept(x: number, y: number): number | undefined

                  onTouchEvent(event: TouchEvent): void

                  onShow(): void

                  onHide(): void
                  
                  onAppear(): void
                  
                  updateTextToolbar(textToolbar: ESObject): void
                }
                
                /*
                 * desc: CAPI 节点包装类
                 */
                export class HarkoRenderNodeV2 implements AbsHarkoRenderNode {
                  private nativeView: ShellRenderView | null

                  constructor(type: string, rootContent: NodeContent, frameCaller: ShellFrameCaller, constraint: HarkoSizeConstraint | undefined, param: ESObject, interopBottomNodeContent: NodeContent, interopTopNodeContent: NodeContent, textToolbar: ESObject, hitTestMode: HitTestMode, nodeController: HarkoNodeController, isPreCompose: boolean${if (extension.enablePerformanceProbe) ", preloadProbe: PreloadProbe | undefined" else ""}, frameNodeId: number | undefined) {
                    this.nativeView = ${if (extension.enablePerformanceProbe) "initRenderNodeWithPreloadProbe" else "initRenderNode"}(type, frameCaller, constraint, rootContent, param, interopBottomNodeContent, interopTopNodeContent, textToolbar, nodeController, hitTestMode, isPreCompose${if (extension.enablePerformanceProbe) ", preloadProbe" else ""}, frameNodeId)
                  }

                  dispose(): void {
                    if (this.nativeView) {
                      this.nativeView.onDestroy()
                      this.nativeView = null
                    }
                  }

                  resize(size: SizeResult, posChanged: boolean): void {
                    console.log("HarkoRenderNode resize: origin " + size.width + " " + size.height + " " + posChanged)
                    this.nativeView?.onSizeChanged(size.width, size.height, posChanged)
                  }

                  resetSize(size: SizeResult): void {
                  }
                  
                  onTouchIntercept(x: number, y: number): number | undefined {
                    return this.nativeView?.onTouchIntercept(x, y)
                  }

                  onTouchEvent(event: TouchEvent): void {
                    if (!this.nativeView) {
                      return
                    }
                    for (const obj of event.changedTouches) {
                      this.nativeView.onTouchChange(obj.id, vp2px(obj.x), vp2px(obj.y), obj.pressure ?? 1)
                    }
                    this.nativeView.onTouchEvent(event.type.valueOf(), event.timestamp, event.changedTouches.length)
                  }

                  onBackPress(): boolean {
                    return this.nativeView ? this.nativeView.onBackPressed() : false
                  }

                  onShow() {
                    console.log("HarkoRenderNodeV2 onShow")
                    this.nativeView?.onShow()
                  }

                  onHide() {
                    console.log("HarkoRenderNodeV2 onHide")
                    this.nativeView?.onHide()
                  }
                  
                  onAppear() {
                    console.log("HarkoRenderNodeV2 onAppear")
                    this.nativeView?.onAppear()
                  }
                  
                  updateTextToolbar(textToolbar: ESObject) {
                    this.nativeView?.updateTextToolbar(textToolbar)
                  }
                }
                
                /*
                 * desc: 通用 RenderNode
                 */
                export class HarkoRenderNode extends RenderNode implements AbsHarkoRenderNode {
                  private nativeView: ShellRenderView | null
                  private child: HarkoChildRenderNode
                
                  constructor(type: string, frameCaller: ShellFrameCaller, constraint: HarkoSizeConstraint | undefined, param: ESObject, interopBottomNodeContent: NodeContent, interopTopNodeContent: NodeContent, textToolbar: ESObject, hitTestMode: HitTestMode, nodeController: HarkoNodeController, isPreCompose: boolean${if (extension.enablePerformanceProbe) ", preloadProbe: PreloadProbe | undefined" else ""}, frameNodeId: number | undefined) {
                    super();
                    this.nativeView = ${if (extension.enablePerformanceProbe) "initRenderNodeWithPreloadProbe" else "initRenderNode"}(type, frameCaller, constraint, undefined, param, interopBottomNodeContent, interopTopNodeContent, textToolbar, nodeController, hitTestMode, isPreCompose${if (extension.enablePerformanceProbe) ", preloadProbe" else ""}, frameNodeId)
                    this.child = this.nativeView.getJsNode() as HarkoChildRenderNode
                    this.child.nativeView = this.nativeView
                    this.appendChild(this.child)
                  }
                
                  dispose(): void {
                    super.dispose()
                    if (this.nativeView) {
                      console.log("HarkoRenderNode dispose")
                      this.nativeView.onDestroy()
                      this.child.nativeView = null
                      this.nativeView = null
                    }
                  }
                  
                  onTouchIntercept(x: number, y: number): number | undefined {
                    return this.nativeView?.onTouchIntercept(x, y)
                  }
                
                  resize(size: SizeResult, posChanged: boolean): void {
                    console.log("HarkoRenderNode resize: origin " + size.width + " " + size.height + " " + posChanged)
                    if (!this.nativeView) {
                      return
                    }
                    let nativeView: ShellRenderView = this.nativeView
                    nativeView.onSizeChanged(size.width, size.height, posChanged)
                    let measureSize: SizeResult = { width: px2vp(nativeView.measureWidth()), height: px2vp(nativeView.measureHeight()) }
                    this.resetSize(measureSize)
                  }
                  
                  resetSize(size: SizeResult): void {
                    console.log("HarkoRenderNode resetSize: " + size.width + " " + size.height)
                    this.size = size
                    this.child.size = size
                  }
                
                  onTouchEvent(event: TouchEvent): void {
                    if (!this.nativeView) {
                      return
                    }
                    for (const obj of event.changedTouches) {
                      this.nativeView.onTouchChange(obj.id, vp2px(obj.x), vp2px(obj.y), obj.pressure ?? 1)
                    }
                    this.nativeView.onTouchEvent(event.type.valueOf(), event.timestamp, event.changedTouches.length)
                  }
                
                  onBackPress(): boolean {
                    return this.nativeView ? this.nativeView.onBackPressed() : false
                  }
                
                  onShow() {
                    console.log("HarkoRenderNode onShow")
                    this.nativeView?.onShow()
                  }
                
                  onHide() {
                    console.log("HarkoRenderNode onHide")
                    this.nativeView?.onHide()
                  }
                  
                  onAppear() {
                    console.log("HarkoRenderNode onAppear")
                    this.nativeView?.onAppear()
                  }
                  
                  updateTextToolbar(textToolbar: ESObject) {
                    this.nativeView?.updateTextToolbar(textToolbar)
                  }
                }
                
                /*
                 * desc: RenderNode 嵌套子节点
                 */
                export class HarkoChildRenderNode extends RenderNode {
                  nativeView: ShellRenderView | null = null

                  draw(context: DrawContext): void {
                    if (this.nativeView) {
                      this.nativeView.draw(context)
                    } else {
                      onJsNodeDraw(context, this)
                    }
                  }
                }

                /*
                 * desc: 帧请求
                 */
                export class ShellFrameCaller implements ShellFrameImportApi, OnDisposeCallback {
                  private uiContext: UIContext
                  private sync: displaySync.DisplaySync
                  private nodeController: HarkoNodeController | undefined = undefined
                  private syncIdSet = new Set<number>()
                  private isSyncStart = false
                  private frameCallback = new RenderFrameCallback()
                  private isDisposed = false

                  constructor(uiContext: UIContext, sync: displaySync.DisplaySync) {
                    this.uiContext = uiContext
                    this.sync = sync
                  }
                  
                  setNodeController(nodeController: HarkoNodeController | undefined): void {
                    this.nodeController = nodeController
                  }

                  onFrame(view: ShellFrameExportApi): void {
                    if (this.isDisposed) {
                      return
                    }
                    this.frameCallback.nativeView = view
                    this.uiContext.postFrameCallback(this.frameCallback)
                  }
                  
                  setHighRefreshRate(id: number, enable: boolean): void {
                    console.log("ShellFrameCaller: setHighRefreshRate " + id + " " + enable)
                    if (this.isDisposed) {
                      return
                    }
                    if (enable) {
                      this.syncIdSet.add(id)
                      if (!this.isSyncStart) {
                        console.log("ShellFrameCaller: setHighRefreshRate start")
                        this.sync.start()
                        this.isSyncStart = true
                      }
                    } else {
                      this.syncIdSet.delete(id)
                      if (this.syncIdSet.size == 0 && this.isSyncStart) {
                        console.log("ShellFrameCaller: setHighRefreshRate stop")
                        this.sync.stop()
                        this.isSyncStart = false
                      }
                    }
                  }
                  
                  clearAndStop() {
                    console.log("ShellFrameCaller: clearAndStop")
                    this.syncIdSet.clear()
                    if(this.isSyncStart) {
                      this.sync.stop()
                      this.isSyncStart = false
                    }
                  }
                  
                  resetSize(width: number, height: number): void {
                    this.nodeController?.resetSize({width: px2vp(width), height: px2vp(height)})
                  }
                  
                  onDispose() {
                    if (this.isDisposed) {
                      return
                    }
                    console.log("ShellFrameCaller dispose")
                    this.isDisposed = true
                    this.nodeController = undefined
                    this.clearAndStop()
                    this.frameCallback.nativeView = null
                  }
                }
                
                /*
                 * desc: 帧回调
                 */
                export class RenderFrameCallback extends FrameCallback {
                  nativeView: ShellFrameExportApi | null = null

                  onFrame(frameTimeNanos: number) {
                    hiTraceMeter.startTrace("RenderFrameCallback.onFrame", 1);
                    this.nativeView?.onFrame(frameTimeNanos)
                    hiTraceMeter.finishTrace("RenderFrameCallback.onFrame", 1);
                  }

                  onIdle(timeLeftInNano: number): void {
                    this.nativeView?.onIdle(timeLeftInNano)
                  }
                }
                
                /*
                 * desc: 节点数据
                 */
                class NodeStatusModify {
                  private opArray: Int32Array | null = null;
                  private opIndex: number = 0;
                  private numberArray: Float32Array | null = null;
                  private numberIndex: number = 0;
                  private svgStrArray: string[] | null = null;
                  private svgStrIndex: number = 0;
                  private nodeArray: HarkoChildRenderNode[] | null[] = [];
                  private nodeIndex: number = 0;
                  private shape = new ShapeClip();

                  getNextSize(): Size {
                    if (this.numberArray == null) {
                      return { width: 0, height: 0 };
                    } else {
                      return {
                        width: this.numberArray[this.numberIndex++],
                        height: this.numberArray[this.numberIndex++]
                      }
                    }
                  }

                  getNextPosition(): Position {
                    if (this.numberArray == null) {
                      return { x: 0, y: 0 };
                    } else {
                      return {
                        x: this.numberArray[this.numberIndex++],
                        y: this.numberArray[this.numberIndex++]
                      }
                    }
                  }

                  getNextRect(): Rect {
                    if (this.numberArray == null) {
                      return {
                        left: 0,
                        top: 0,
                        right: 0,
                        bottom: 0
                      };
                    } else {
                      return {
                        left: this.numberArray[this.numberIndex++],
                        top: this.numberArray[this.numberIndex++],
                        right: this.numberArray[this.numberIndex++],
                        bottom: this.numberArray[this.numberIndex++]
                      }
                    }
                  }
                  
                  getNextRoundRect(): RoundRect {
                    if (this.numberArray == null) {
                      return {
                        rect: { left: 0, top: 0, right: 0, bottom: 0 },
                        corners: {
                          topLeft: { x: 0, y: 0 },
                          topRight: { x: 0, y: 0 },
                          bottomLeft: { x: 0, y: 0 },
                          bottomRight: { x: 0, y: 0 }
                        }
                      };
                    } else {
                      return {
                        rect: {
                          left: this.numberArray[this.numberIndex++],
                          top: this.numberArray[this.numberIndex++],
                          right: this.numberArray[this.numberIndex++],
                          bottom: this.numberArray[this.numberIndex++]
                        },
                        corners: {
                          topLeft: { x: this.numberArray[this.numberIndex++], y: this.numberArray[this.numberIndex++] },
                          topRight: { x: this.numberArray[this.numberIndex++], y: this.numberArray[this.numberIndex++] },
                          bottomLeft: { x: this.numberArray[this.numberIndex++], y: this.numberArray[this.numberIndex++] },
                          bottomRight: { x: this.numberArray[this.numberIndex++], y: this.numberArray[this.numberIndex++] }
                        }
                      }
                    }
                  }

                  getNextMatrix(): Matrix4 {
                    if (this.numberArray == null) {
                      return [
                        1, 0, 0, 0,
                        0, 1, 0, 0,
                        0, 0, 1, 0,
                        0, 0, 0, 1
                      ]
                    }
                    return [
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++],
                      this.numberArray[this.numberIndex++]
                    ]
                  }
                  
                  getNextCommandPath(): string {
                    if (this.svgStrArray == null) {
                      return "";
                    }
                    return this.svgStrArray[this.svgStrIndex++];
                  }

                  doModify() {
                    this.opIndex = 0;
                    this.numberIndex = 0;
                    this.nodeIndex = 0;
                    this.svgStrIndex = 0;
                    if (this.opArray != null) {

                      for (this.opIndex = 0; this.opIndex < this.opArray.length; this.opIndex++) {
                        const op = this.opArray[this.opIndex];
                        const node = this.nodeArray[this.nodeIndex++];
                        if (node == null) {
                          console.log("NodeStatusModify.doModify: null node with op", op);
                          continue;
                        }
                        switch (op) {
                          case 0:
                            node.invalidate();
                            break;
                          case 1:
                            node.removeChild(this.nodeArray[this.nodeIndex++]);
                            break;
                          case 2:
                            node.insertChildAfter(this.nodeArray[this.nodeIndex++], this.nodeArray[this.nodeIndex++]);
                            break;
                          case 3:
                            node.clearChildren();
                            break;
                          case 10:
                            node.appendChild(this.nodeArray[this.nodeIndex++]);
                            break;
                          case 4:
                            node.size = this.getNextSize();
                            break;
                          case 5:
                            node.position = this.getNextPosition();
                            break;
                          case 6:
                            node.transform = this.getNextMatrix();
                            break;
                          case 7:
                            this.shape.setRectShape(this.getNextRect());
                            node.shapeClip = this.shape;
                            break;
                          case 8:
                            this.shape.setRoundRectShape(this.getNextRoundRect())
                            node.shapeClip = this.shape;
                            break;
                          case 9:
                            this.shape.setCommandPath({commands: this.getNextCommandPath()});
                            node.shapeClip = this.shape;
                            break;
                          default:
                            console.log("NodeStatusModify.doModify: invalid op", op);
                            break;
                        }
                      }
                    }
                    this.nodeArray = []
                  }
                }

                const regex = /^[A-Za-z0-9]+${'$'}/

                function getComposeUUID(composeId: string, param: ESObject, useCApi: boolean, beforePre: boolean): string {
                  if (param instanceof Object) {
                    if (typeof param.getUUID !== 'function' || typeof param.UUID !== 'string') {
                      console.log("invalid param for preCompose")
                      return ""
                    }
                    if (param.UUID == "") {
                      if (beforePre) {
                        param.UUID = param.getUUID()
                      } else {
                        console.log("no cached UUID of param")
                        return ""
                      }
                    }
                    if (!regex.test(param.UUID)) {
                      console.log("invalid UUID of param")
                      return ""
                    }
                    return `${'$'}{composeId}-${'$'}{param.UUID}-${'$'}{useCApi}`
                  }
                  return `${'$'}{composeId}-${'$'}{param}-${'$'}{useCApi}`
                }
                
                export const ControllerMap = new Map<string, HarkoNodeController>()
                
                export function preCompose(composeId: string, hitTestMode: HitTestMode, options: ComposeViewOptions, param: ESObject, size: SizeResult = {width: 0, height: 0}): ${if (extension.enablePerformanceProbe) "PreloadHandler | undefined" else "string"} {
                  let useCApi = isUseRenderCApi(options)
                  let composeUUID: string = getComposeUUID(composeId, param, useCApi, true)
                  if (composeUUID == "") {
                    return ${if (extension.enablePerformanceProbe) "undefined" else "\"\""}
                  }
                  if (!ControllerMap.has(composeUUID)) {
                    let sync: displaySync.DisplaySync = displaySync.create()
                    sync.setExpectedFrameRateRange({ expected: 120, min: 0, max: 120 })
                    let frameCaller: ShellFrameCaller = new ShellFrameCaller(ComposeInitializer.getUIContext(), sync)
                    let preNodeController = new HarkoNodeController(composeId, frameCaller, undefined, options.constraint, param, new NodeContent(), new NodeContent(), undefined, hitTestMode, options.disposeBus !== undefined, useCApi, true${if (extension.enablePerformanceProbe) ", new PreloadHandler(composeUUID)" else ""}, size)
                    preNodeController.ComposeUUID = composeUUID
                    ControllerMap.set(composeUUID, preNodeController)
                    return ${if (extension.enablePerformanceProbe) "preNodeController.preloadProbe" else "composeUUID"}
                  }
                  console.log(composeUUID + " page preCompose already done")
                  return ${if (extension.enablePerformanceProbe) "ControllerMap.get(composeUUID)?.preloadProbe" else "composeUUID"}
                }
                
                export function disposePreCompose(UUID: string) {
                  if (ControllerMap.has(UUID)) {
                    let nodeController = ControllerMap.get(UUID)
                    if (!nodeController?.hasUsed) {
                      nodeController?.onDispose()
                    }
                  }
                }
                
                /*
                 * desc: 通用 NodeController
                 */
                 export class HarkoNodeController extends NodeController implements OnDisposeCallback {
                   private rootNode: FrameNode | null = null
                   private harkoNode: AbsHarkoRenderNode | null = null
                   private isVisible = false
                   
                   private nodeType: string
                   frameCaller: ShellFrameCaller | null
                   backEventBus: OnBackPressEventBus | undefined
                   private nodeConstraint: HarkoSizeConstraint | undefined
                   private nodeParam: ESObject | undefined
                   interopBottomNodeContent: NodeContent
                   interopTopNodeContent: NodeContent
                   private textToolbar: WeakRef<ESObject> | undefined
                   private hitTestMode: HitTestMode
                   private manualDestroy: boolean
                   private blockTouchEvents: boolean = false
                   private useCApi: boolean
                   private rootContent: NodeContent | null = null
                   private nodeBuilder: BuilderNode<[NodeContent]> | null = null;
                   private wrapBuilder: WrappedBuilder<[NodeContent]> = wrapBuilder(ContentBuilder)
                   private isPreCompose: boolean
                   ComposeUUID: string = ""
                   hasUsed: boolean
                   ${if (extension.enablePerformanceProbe) "preloadProbe: PreloadHandler | undefined" else ""}

                   constructor(type: string, frameCaller: ShellFrameCaller, backEventBus: OnBackPressEventBus | undefined, constraint: HarkoSizeConstraint | undefined, param: ESObject, interopBottomNodeContent: NodeContent, interopTopNodeContent: NodeContent, textToolbar: ESObject, hitTestMode: HitTestMode, manualDestroy: boolean, useCApi: boolean, isPreCompose: boolean${if (extension.enablePerformanceProbe) ", preloadProbe: PreloadHandler | undefined" else ""}, size: SizeResult = { width: 0, height: 0}) {
                     super();
                     
                     this.nodeType = type
                     this.frameCaller = frameCaller
                     this.backEventBus = backEventBus
                     this.nodeConstraint = constraint
                     this.nodeParam = param
                     this.interopBottomNodeContent = interopBottomNodeContent
                     this.interopTopNodeContent = interopTopNodeContent
                     this.textToolbar = textToolbar ? new WeakRef(textToolbar) : undefined
                     this.hitTestMode = hitTestMode
                     this.manualDestroy = manualDestroy
                     this.useCApi = useCApi
                     this.isPreCompose = isPreCompose
                     this.hasUsed = this.isPreCompose ? false : true
                     ${if (extension.enablePerformanceProbe) "this.preloadProbe = preloadProbe" else ""}
                     if (this.isPreCompose) {
                       if (this.useCApi) {
                         let content = new NodeContent()
                         this.rootContent = content
                         this.harkoNode = new HarkoRenderNodeV2(this.nodeType, this.rootContent!!, this.frameCaller, this.nodeConstraint, this.nodeParam, this.interopBottomNodeContent, this.interopTopNodeContent, this.textToolbar?.deref(), this.hitTestMode, this, this.isPreCompose${if (extension.enablePerformanceProbe) ", this.preloadProbe" else ""}, undefined)
                       } else {
                         this.harkoNode = new HarkoRenderNode(this.nodeType, this.frameCaller, this.nodeConstraint, this.nodeParam, this.interopBottomNodeContent, this.interopTopNodeContent, this.textToolbar?.deref(), this.hitTestMode, this, this.isPreCompose${if (extension.enablePerformanceProbe) ", this.preloadProbe" else ""}, undefined)
                       }
                       this.frameCaller.setNodeController(this)
                       if (size.width == 0 || size.height == 0) {
                         let screenSize = display.getDefaultDisplaySync()
                         size.width = screenSize.width
                         size.height = screenSize.height
                       }
                       this.onResize(size, false)
                     }
                   }

                   makeNode(uiContext: UIContext): FrameNode | null {
                     if (this.useCApi) {
                       if (!this.nodeBuilder) {
                         let content: NodeContent
                         if (this.rootContent) {
                           content = this.rootContent
                         } else {
                           content = new NodeContent()
                           this.rootContent = content
                         }
                         
                         this.nodeBuilder = new BuilderNode(uiContext)
                         this.nodeBuilder.build(this.wrapBuilder, content)
                         const frameNodeId = this.nodeBuilder.getFrameNode()?.getUniqueId()
                         if (!this.harkoNode && this.frameCaller) {
                           this.harkoNode = new HarkoRenderNodeV2(this.nodeType, content, this.frameCaller, this.nodeConstraint, this.nodeParam, this.interopBottomNodeContent, this.interopTopNodeContent, this.textToolbar?.deref(), this.hitTestMode, this, this.isPreCompose${if (extension.enablePerformanceProbe) ", this.preloadProbe" else ""}, frameNodeId)
                           if (this.backEventBus) {
                             this.backEventBus.addCallback(this.harkoNode)
                           }
                         }
                       }
                       return this.nodeBuilder.getFrameNode()
                     }
                     this.rootNode = new FrameNode(uiContext)
                     const rootRenderNode = this.rootNode.getRenderNode()
                     const frameNodeId = this.rootNode.getUniqueId()
                     if (rootRenderNode !== null) {
                       if (this.harkoNode instanceof HarkoRenderNode) {
                         if (this.isPreCompose) {
                           rootRenderNode.appendChild(this.harkoNode)
                            if (this.backEventBus) {
                              this.backEventBus.addCallback(this.harkoNode)
                            }
                         }
                       } else if (this.frameCaller) {
                         const harkoNode = new HarkoRenderNode(this.nodeType, this.frameCaller, this.nodeConstraint, this.nodeParam, this.interopBottomNodeContent, this.interopTopNodeContent, this.textToolbar?.deref(), this.hitTestMode, this, this.isPreCompose${if (extension.enablePerformanceProbe) ", this.preloadProbe" else ""}, frameNodeId)
                         this.harkoNode = harkoNode
                         if (this.backEventBus) {
                           this.backEventBus.addCallback(this.harkoNode)
                         }
                         rootRenderNode.appendChild(harkoNode)
                       }
                     }
                     this.rootNode.commonAttribute.hitTestBehavior(HitTestMode.Transparent)
                     return this.rootNode
                   }
                   
                   onTouchIntercept(x: number, y: number): number | undefined {
                     return this.harkoNode?.onTouchIntercept(x, y)
                   }

                   onResize(size: SizeResult, posChanged: boolean): void {
                     this.harkoNode?.resize(size, posChanged)
                   }
                   
                   resetSize(size: SizeResult): void {
                     this.harkoNode?.resetSize(size)
                   }
                   
                   blockTouchEvent(block: boolean): void {
                     this.blockTouchEvents = block
                   }

                   onTouchEvent(event: TouchEvent): void {
                     try {
                       if (!this.blockTouchEvents) {
                         this.harkoNode?.onTouchEvent(event)
                       }
                     } catch (e) {
                       console.error(`HarkoNodeController::onTouchEvent failed. ` + e.message);
                     }
                   }

                   onPageShow(): void {
                     if (this.isVisible) return
                     this.harkoNode?.onShow()
                     this.isVisible = true
                   }

                   onPageHide(): void {
                     if (!this.isVisible) return
                     this.harkoNode?.onHide()
                     this.isVisible = false
                   }
                   
                   onVisibleChange(isExpanding: boolean, currentRatio: number): void {
                     console.log(`HarkoNodeController: onVisibleChange isExpanding ` + isExpanding + " " + currentRatio + " visible=" + this.isVisible)
                     if (!this.harkoNode) {
                       return
                     }
                     if (isExpanding) {
                       if (!this.isVisible && currentRatio > 0) {
                         this.harkoNode.onShow()
                         this.isVisible = true
                       }
                     } else {
                       if (this.isVisible && currentRatio == 0) {
                         this.harkoNode.onHide()
                         this.isVisible = false
                       }
                     }
                   }
                   
                   onAppear(): void {
                     this.harkoNode?.onAppear()
                   }

                   onBackPress(): boolean {
                     return this.harkoNode ? this.harkoNode.onBackPress() : false
                   }
                   
                   updateTextToolbar(textToolbar: ESObject) {
                     this.textToolbar = new WeakRef(textToolbar)
                     this.harkoNode?.updateTextToolbar(this.textToolbar?.deref())
                   }
                   
                   aboutToDisappear(): void {
                     console.log(`HarkoNodeController: aboutToDisappear`)
                     if (this.manualDestroy) {
                       return
                     }
                     this.onDispose()
                   }
                   
                   onDispose(): void {
                     console.log(`HarkoNodeController: onDispose`)
                     if (this.harkoNode) {
                       if (this.harkoNode instanceof HarkoRenderNode) {
                         this.harkoNode.clearChildren()
                       }
                       this.harkoNode.dispose()
                       this.harkoNode = null
                     }
                     this.rootNode?.getRenderNode()?.clearChildren()
                     this.rootNode?.clearChildren()
                     this.frameCaller = null
                     this.backEventBus = undefined
                     this.nodeConstraint = undefined
                     this.nodeParam = undefined
                     if (this.isPreCompose) {
                       ControllerMap.delete(this.ComposeUUID)
                     }
                   }
                 }
                 
                 /*
                 * desc: CAPI RenderNode 固定挂载节点
                 */
                 @Builder
                 export function ContentBuilder(rootSlot: NodeContent) {
                   Stack() {
                     ContentSlot(rootSlot)
                   }
                   .height('auto')
                   .width('auto')
                 }
                
                /*
                 * desc: ComposeView 注解生成参数
                 */
                export declare interface ComposeViewMetaData {
                  composeId: string
                  hitTestMode: HitTestMode
                  withInterop: boolean
                }
                
                const EmptyComposeViewMetaData: ComposeViewMetaData = {
                  composeId: "",
                  hitTestMode: HitTestMode.Default,
                  withInterop: false,
                }
                 
                /*
                 * desc: ComposeView 可选参数
                 */
                export declare interface ComposeViewOptions {
                  backEventBus?: OnBackPressEventBus
                  displaySync?: displaySync.DisplaySync
                  constraint?: HarkoSizeConstraint
                  maxPosDiff?: number
                  manualShowAndHide?: boolean
                  disposeBus?: OnDisposeCollection
                  useCApi?: boolean
                  ${if (extension.enablePerformanceProbe) "preloadProbe?: PreloadHandler" else ""}
                }
                
                function isUseRenderCApi(options: ComposeViewOptions): boolean {
                  if (!renderNodeCApiSupported) {
                    return false
                  }
                  return options.useCApi === undefined ? isRenderNodeCApiDefaultEnable : options.useCApi
                }
                
                /*
                 * desc: 通用 ComposeView
                 */
                @Component
                export struct ComposeView {
                  @Require metaData: ComposeViewMetaData = EmptyComposeViewMetaData
                  @Require param?: ESObject = undefined
                  @Require options: ComposeViewOptions = {}
                  private interopBottomNodeContent = new NodeContent();
                  private interopTopNodeContent = new NodeContent();
                  private nodeController: HarkoNodeController | undefined
                  private frameCaller: ShellFrameCaller | undefined = undefined

                  @State private textToolbarConfig: TextToolbarConfig = new TextToolbarConfig()
                  private touchInterceptResult: number | undefined = HitTestMode.Default
                  
                  updateToolbar(showToolbar: boolean, showCopy: boolean, showPaste: boolean, showCut: boolean, showSelectAll: boolean,
                    left: number, top: number, right: number, bottom: number, callback: TextToolBarCallback) {
                    
                    console.log("[OhosTextToolbar] showToolbar: " + showToolbar + ", showCopy: " + showCopy + ", showPaste: " + showPaste + ", showCut: " + showCut + ", showSelectAll: " + showSelectAll + ", left: " + left + ", top: " + top + ", right: " + right + ", bottom: " + bottom)
                
                    if (this.textToolbarConfig.showToolbar == showToolbar
                      && this.textToolbarConfig.showCopy == showCopy
                      && this.textToolbarConfig.showPaste == showPaste
                      && this.textToolbarConfig.showCut == showCut
                      && this.textToolbarConfig.showSelectAll == showSelectAll
                      && this.textToolbarConfig.offsetX == px2vp(left)
                      && this.textToolbarConfig.offsetY == px2vp(top)
                      && this.textToolbarConfig.width == px2vp(right - left)
                      && this.textToolbarConfig.height == px2vp(bottom - top)) {
                      console.log(`[OhosTextToolbar] skip for unchanged value`)
                      return
                    }
                
                    let newTextToolbarConfig = new TextToolbarConfig()
                    newTextToolbarConfig.showToolbar = showToolbar
                    newTextToolbarConfig.showCopy = showCopy
                    newTextToolbarConfig.showPaste = showPaste
                    newTextToolbarConfig.showCut = showCut
                    newTextToolbarConfig.showSelectAll = showSelectAll
                    newTextToolbarConfig.offsetX = px2vp(left)
                    newTextToolbarConfig.offsetY = px2vp(top)
                    newTextToolbarConfig.width = px2vp(right - left)
                    newTextToolbarConfig.height = px2vp(bottom - top)
                    newTextToolbarConfig.callback = callback
                    
                    this.textToolbarConfig = newTextToolbarConfig
                  }
                  
                  onBackPress(): boolean | void {
                    if (this.options.backEventBus) {
                      return this.options.backEventBus.onBackPress()
                    }
                    return this.nodeController?.onBackPress()
                  }
                  
                  dispatchOnShow() : void {
                    if (this.options.manualShowAndHide === true) {
                      this.nodeController?.onPageShow()
                    }
                  }
                  
                  dispatchOnHide() : void {
                    if (this.options.manualShowAndHide === true) {
                      this.nodeController?.onPageHide()
                    }
                  }
                  
                  aboutToAppear(): void {
                    ComposeInitializer.onComposeViewLoad(this.metaData.composeId)
                    console.log("ComposeView aboutToAppear")
                    const uiContext: common.UIAbilityContext = getContext() as common.UIAbilityContext
                    onDensityPixelsChanged(vp2px(1))
                    onFontSizeScaleChanged(uiContext.config.fontSizeScale ?? 1)
                    onFontWeightScaleChanged(uiContext.config.fontWeightScale ?? 1)
                    onSystemFontIdChanged(uiContext.config.fontId ?? '')
                    let useCApi = isUseRenderCApi(this.options)
                    if (!this.nodeController) {
                      let composeUUID = getComposeUUID(this.metaData.composeId, this.param, useCApi, false)
                      if (composeUUID != "" && ControllerMap.has(composeUUID)) {
                        this.nodeController = ControllerMap.get(composeUUID)
                        this.nodeController!.hasUsed = true
                        this.nodeController!.backEventBus = this.options.backEventBus
                        this.nodeController?.updateTextToolbar(this)
                        this.interopBottomNodeContent = this.nodeController!.interopBottomNodeContent
                        this.interopTopNodeContent = this.nodeController!.interopTopNodeContent
                        this.frameCaller = this.nodeController?.frameCaller!!
                      } else {
                        if (!this.frameCaller) {
                          let sync: displaySync.DisplaySync = this.options.displaySync ?? displaySync.create()
                          if (this.options.displaySync === undefined) {
                            sync.setExpectedFrameRateRange({ expected: 120, min: 0, max: 120 })
                          }
                          this.frameCaller = new ShellFrameCaller(this.getUIContext(), sync)
                        }
                        this.nodeController = new HarkoNodeController(this.metaData.composeId, this.frameCaller, this.options.backEventBus, this.options.constraint, this.param, this.interopBottomNodeContent, this.interopTopNodeContent, this, this.metaData.hitTestMode, this.options.disposeBus !== undefined, useCApi, false${if (extension.enablePerformanceProbe) ", this.options.preloadProbe" else ""})
                        this.frameCaller.setNodeController(this.nodeController)
                      }
                    }
                    activeComposeView.set(this.metaData.composeId, this)
                    if (this.options.disposeBus !== undefined) {
                      this.options.disposeBus.addCallback(this.nodeController)
                      this.options.disposeBus.addCallback(this.frameCaller)
                      if (this.options.backEventBus) {
                        this.options.disposeBus.addCallback(this.options.backEventBus)
                      }
                    }
                  }
                  
                  aboutToDisappear(): void {
                    ComposeInitializer.onComposeViewDispose(this.metaData.composeId)
                    console.log("ComposeView aboutToDisappear")
                    if (this.options.disposeBus === undefined) {
                      if (this.frameCaller) {
                        this.frameCaller.onDispose()
                        this.frameCaller = undefined
                      }
                      if (this.options.backEventBus) {
                        this.options.backEventBus.onDispose()
                      }
                    }
                    activeComposeView.delete(this.metaData.composeId)
                  }
                  
                  private checkPosChanged(oldVal ?: Length, newVal ?: Length): boolean {
                    let maxPosDiff = this.options.maxPosDiff ?? -1
                    if (maxPosDiff < 0) {
                      return false
                    }
                    if (oldVal == undefined || newVal == undefined) {
                      return false
                    }
                    if (typeof oldVal === 'number' && typeof newVal === 'number') {
                      return Math.abs(oldVal - newVal) > maxPosDiff
                    }
                    return oldVal != newVal
                  }

                  build() {
                    Stack(){
                      if (this.metaData.withInterop) {
                        ContentSlot(this.interopBottomNodeContent)
                      }
                      Stack() {
                        Stack() {
                          NodeContainer(this.nodeController)
                            .focusable(true)// 可响应键盘事件
                            .onAreaChange((oldValue: Area, newValue: Area) => {
                              const posChanged = this.checkPosChanged(oldValue.globalPosition.x, newValue.globalPosition.x) ||
                              this.checkPosChanged(oldValue.globalPosition.y, newValue.globalPosition.y)
                              this.nodeController?.onResize({
                                width: vp2px(newValue.width as number),
                                height: vp2px(newValue.height as number),
                              }, posChanged)
                            })
                            .onVisibleAreaChange([0.0, 1.0], (isExpanding, currentRatio) => {
                              if (this.options.manualShowAndHide !== true) {
                                this.nodeController?.onVisibleChange(isExpanding, currentRatio)
                              }
                            })
                            .onAppear(() => {
                              this.nodeController?.onAppear()
                            })
                            .width(this.options.constraint?.widthForLength() ?? '100%')
                            .height(this.options.constraint?.heightForLength() ?? '100%')
                            .hitTestBehavior(this.metaData.hitTestMode)
                        }
                      }
                      .onTouchIntercept((event) => {
                        this.nodeController?.blockTouchEvent(false)
                        if (!this.metaData.withInterop) {
                          return HitTestMode.Default
                        }
                        if (event && event.type == TouchType.Down && event.touches && event.touches[0]) {
                          let touch = event.touches[0]
                          let result = this.nodeController?.onTouchIntercept(vp2px(touch.x), vp2px(touch.y))
                          this.touchInterceptResult = result
                          switch (result) {
                            case HitTestMode.Default:
                              // 默认行为，未命中混排 ArkUI
                              return HitTestMode.Default
                            case HitTestMode.Block:
                              // 命中混排 ArkUI，但是不允许 ArkUI 响应事件
                              return HitTestMode.None
                            case HitTestMode.Transparent:
                              // 命中混排 ArkUI，同时允许 ArkUI 和 Compose 响应事件
                              return HitTestMode.Transparent
                            case HitTestMode.None:
                              // 命中混排 ArkUI，只允许 ArkUI 响应事件
                              this.nodeController?.blockTouchEvent(true)
                              return HitTestMode.Transparent
                          }
                        }
                        this.touchInterceptResult = HitTestMode.Default
                        return HitTestMode.Default
                      })
                      .onTouch((event) => {
                        if (this.touchInterceptResult == HitTestMode.Block) {
                          // 命中混排 ArkUI，但是不允许 ArkUI 响应事件
                          event.stopPropagation()
                        }
                      })
                      if (this.metaData.withInterop) {
                        Stack() {
                           ContentSlot(this.interopTopNodeContent)
                        }.hitTestBehavior(HitTestMode.Transparent)
                      }
                      // TextToolbar展示的锚点
                      if (this.textToolbarConfig.showToolbar) {
                        Stack()
                          .position({ x: this.textToolbarConfig.offsetX, y: this.textToolbarConfig.offsetY })
                          .width(this.textToolbarConfig.width)
                          .height(this.textToolbarConfig.height)
                          .offset({ x: this.textToolbarConfig.offsetX, y: this.textToolbarConfig.offsetY })
                          .bindPopup(true, {
                            builder: this.textToolbarBuilder,
                            placement: Placement.Top,
                            // offset: {x: this.textToolbarConfig.offsetX, y: this.textToolbarConfig.offsetY},
                            mask: false,
                            enableArrow: false,
                            keyboardAvoidMode: KeyboardAvoidMode.NONE,
                          })
                          .hitTestBehavior(HitTestMode.Transparent)
                      }
                    }.alignContent(Alignment.TopStart)
                  }
                  
                  // Popup构造器定义弹框内容
                  @Builder
                  textToolbarBuilder() {
                    Row({ space: 2 }) {
                      if (this.textToolbarConfig.showCopy) {
                        this.textToolbarItem('复制', () => {
                          this.textToolbarConfig.callback?.onCopyRequested()
                        })
                      }
                      if (this.textToolbarConfig.showPaste) {
                        // 使用系统安全控件，避免权限问题
                        PasteButton({ text: PasteDescription.PASTE})
                          .padding({ left: 15, right: 15, top: 12, bottom: 12 })
                          .fontSize(16)
                          .fontColor(Color.Black)
                          .backgroundColor(Color.White)
                          .fontWeight(FontWeight.Normal)
                          .onClick((event: ClickEvent, result: PasteButtonOnClickResult) => {
                            if (PasteButtonOnClickResult.SUCCESS === result) {
                              pasteboard.getSystemPasteboard().getData((err: BusinessError, pasteData: pasteboard.PasteData) => {
                                if (err) {
                                  console.error("[OhosTextToolbar] Failed to get paste data. Code is" + err.code + ",  message is " + err.message);
                                  this.textToolbarConfig.callback?.onPasteRequested(undefined)
                                  return;
                                }
                                let text = pasteData.getPrimaryText()
                                console.error(`[OhosTextToolbar]  get paste data: ` + text);
                                this.textToolbarConfig.callback?.onPasteRequested(text)
                              });
                            } else {
                              console.error("[OhosTextToolbar] Failed to get paste data");
                              this.textToolbarConfig.callback?.onPasteRequested(undefined)
                            }
                          })
                      }
                      if (this.textToolbarConfig.showCut) {
                        this.textToolbarItem('剪切', () => {
                          this.textToolbarConfig.callback?.onCutRequested()
                        })
                      }
                      if (this.textToolbarConfig.showSelectAll) {
                        this.textToolbarItem('全选', () => {
                          this.textToolbarConfig.callback?.onSelectAllRequested()
                        })
                      }
                    }
                    .backgroundColor(Color.White)
                    .borderRadius(12)
                  }
                
                  @Builder
                  textToolbarItem(name: string, onClick: () => void) {
                    Column() {
                      Text(name)
                        .fontSize(16)
                        .fontColor(Color.Black)
                    }
                    .padding({ left: 15, right: 15, top: 12, bottom: 12 })
                    .onClick(() => {
                      onClick();
                    });
                  }
                }
                
                class TextToolbarConfig {
                  showToolbar: boolean = false
                  showCopy: boolean = false
                  showPaste: boolean = false
                  showCut: boolean = false
                  showSelectAll: boolean = false
                  offsetX: number = 0
                  offsetY: number = 0
                  width: number = 0
                  height: number = 0
                  callback?: TextToolBarCallback = undefined
                }
                
                // 统一初始化入口
                export class ComposeInitializer {
                  static onComposeViewLoad: (composeId: string) => void = () => {}
                  static onComposeViewDispose: (composeId: string) => void = () => {}
                  static windowStage: WeakRef<window.WindowStage> | undefined
                  static window: WeakRef<window.Window> | undefined
                  static getUIContext(): UIContext {
                    return ComposeInitializer.window?.deref()?.getUIContext() ?? ComposeInitializer.windowStage?.deref()?.getMainWindowSync().getUIContext()!
                  }
                  static init(context: Context, windowStage: window.WindowStage, 
                    onComposeViewLoad: (composeId: string) => void = () => {},
                    onComposeViewDispose: (composeId: string) => void = () => {},
                  ) {
                    ComposeInitializer.onComposeViewLoad = onComposeViewLoad
                    ComposeInitializer.onComposeViewDispose = onComposeViewDispose
                    ComposeInitializer.windowStage = new WeakRef(windowStage)
                    windowStage.getMainWindow().then((window) => {
                      ComposeInitializer.window = new WeakRef(window)
                    })
                    initRenderNodeContext()
                    setCApiFixed(renderNodeCApiFixed)
                    let appContext = context.getApplicationContext()
                    let uiContext: common.UIAbilityContext = getContext() as common.UIAbilityContext
                    let resManager = appContext.resourceManager
                    let fontSizeScale = uiContext?.config?.fontSizeScale
                    let fontWeightScale = uiContext?.config?.fontWeightScale
                    let fontId = uiContext?.config?.fontId
                    let language = I18n.System.getSystemLanguage()
                    let region = I18n.System.getSystemRegion()
                    let is24HourClock: boolean = I18n.System.is24HourClock()
                
                    if (fontSizeScale) {
                      onFontSizeScaleChanged(fontSizeScale)
                    }
                    if (fontWeightScale) {
                      onFontWeightScaleChanged(fontWeightScale)
                    }
                    if (fontId) {
                      onSystemFontIdChanged(fontId)
                    }
                    setResourceManager(resManager)
                    onDensityPixelsChanged(vp2px(1))
                    onLanguageChanged(language, region)
                    on24HourFormatChanged(is24HourClock)
                
                    appContext.on("environment", {
                      onConfigurationUpdated(config) {
                        console.log('KmpLogger: onConfigurationUpdated ' + config.fontSizeScale + ' ' + config.fontWeightScale + ' ' + config.fontId)
                        let region = I18n.System.getSystemRegion()
                        let language = I18n.System.getSystemLanguage()
                        onFontSizeScaleChanged(config.fontSizeScale ?? 1)
                        onFontWeightScaleChanged(config.fontWeightScale ?? 1)
                        onSystemFontIdChanged(config.fontId ?? '')
                        onLanguageChanged(config.language ?? language, region)
                      },
                      onMemoryLevel() {}
                    })
                
                    windowStage.getMainWindow().then((windowClass) => {
                      windowClass.on('keyboardHeightChange', (height: number) => {
                        onKeyboardHeightChanged(height)
                      })
                      ComposeInitializer.updateWindowInsets(windowClass)
                      console.log(`KmpLogger: fist updateWindowInsets`)
                      windowClass.on('windowSizeChange', (size) => {
                        console.log(`KmpLogger: windowSizeChange ` + size.width + " " + size.height)
                        ComposeInitializer.updateWindowInsets(windowClass)
                      })
                
                      windowClass.on('avoidAreaChange', (avoidAreaOptions) => {
                        ComposeInitializer.updateWindowInsets(windowClass)
                      })
                    })
                  }
                
                  static updateWindowInsets(windowClass: window.Window) {
                    try {
                      let windowReact = windowClass.getWindowProperties().windowRect
                      let width = windowReact.width
                      let height = windowReact.height
                
                      console.log(`KmpLogger: updateWindowInsets ` + width + " " + height)
                
                      let cutout = windowClass.getWindowAvoidArea(window.AvoidAreaType.TYPE_CUTOUT)
                      let system = windowClass.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM)
                      let systemGesture = windowClass.getWindowAvoidArea(window.AvoidAreaType.TYPE_SYSTEM_GESTURE)
                      let navigation = windowClass.getWindowAvoidArea(window.AvoidAreaType.TYPE_NAVIGATION_INDICATOR)
                
                      let cutoutWindowInsets = ComposeInitializer.avoidAreaToWindowInsets(cutout, width, height)
                      let safeAreaWindowInsets = ComposeInitializer.avoidAreaToWindowInsets(system, width, height)
                      safeAreaWindowInsets.bottom = Math.max(height - navigation.bottomRect.top, safeAreaWindowInsets.bottom)
                
                      // safeAreaWindowInsets = ComposeInitializer.merge(safeAreaWindowInsets, cutoutWindowInsets)
                
                      let systemGestureWindowInsets = ComposeInitializer.avoidAreaToWindowInsets(systemGesture, width, height)
                
                      onWindowInsetsChanged(safeAreaWindowInsets, cutoutWindowInsets, systemGestureWindowInsets)
                    } catch (e) {
                      console.log(`KmpLogger: updateWindowInsets error ` + e)
                    }
                  }
                
                  static avoidAreaToWindowInsets(avoidArea: window.AvoidArea, width: number, height: number): WindowInsetsRect {
                    let windowInsets: WindowInsetsRect = new WindowInsetsRect()
                    windowInsets.left = avoidArea.leftRect.left + avoidArea.leftRect.width
                    windowInsets.top = avoidArea.topRect.top + avoidArea.topRect.height
                    if (avoidArea.rightRect.width > 0) {
                      windowInsets.right = width - avoidArea.rightRect.left
                    }
                    if (avoidArea.bottomRect.height > 0) {
                      windowInsets.bottom = height - avoidArea.bottomRect.top
                    }
                    return windowInsets
                  }
                
                  static merge(first: WindowInsetsRect, second: WindowInsetsRect): WindowInsetsRect {
                    first.left = Math.max(first.left, second.left)
                    first.top = Math.max(first.top, second.top)
                    first.right = Math.max(first.right, second.right)
                    first.bottom = Math.max(first.bottom, second.bottom)
                
                    return first
                  }
                  
                  static onConfigUpdate() {
                    const language = I18n.System.getSystemLanguage()
                    const region = I18n.System.getSystemRegion()
                    onLanguageChanged(language, region)
                
                    let is24HourClock: boolean = I18n.System.is24HourClock()
                    on24HourFormatChanged(is24HourClock)
                  }
                }
                
                /**
                 * 获取当前活跃的 ComposeView
                 */
                export const activeComposeView = new Map<string, ComposeView>()
                
                /*
                 * desc: 嵌套滑动 Scroller 包装
                 */
                 export class NestedScrollable implements ImportScrollable {
                   scroller: Scroller

                   constructor(scroller: Scroller) {
                     this.scroller = scroller
                   }

                   scrollBy(x: number, y: number): void {
                     this.scroller.scrollBy(`${"\${x}"}px`, `${"\${y}"}px`)
                   }

                   getLastScrollOffset(direction: boolean): number {
                     if (direction) {
                       let rect = this.scroller.getItemRect(0)
                       console.log("KmpLogger getLastScrollOffset " + rect.y + " " + rect.height)
                       if (rect.height != 0) {
                         return -vp2px(rect.y)
                       }
                     }
                     return 16384
                   }
                 }
                 
                 /*
                 * desc: 嵌套滑动 WebviewController 包装
                 */
                 export class NestedWebScrollable implements ImportScrollable {
                   controller: webview.WebviewController

                   constructor(controller: webview.WebviewController) {
                     this.controller = controller;
                   }

                   scrollBy(x: number, y: number): void {
                     this.controller.scrollBy(px2vp(x), px2vp(y))
                   }

                   getLastScrollOffset(direction: boolean): number {
                     let offset = this.controller.getScrollOffset().y
                     if (direction) {
                       return offset
                     }
                     let height = this.controller.getPageHeight()
                     return Math.max(0, height - offset)
                   }
                 }
                 
                 /*
                 * desc: 嵌套滑动 Webview 触摸事件回调封装
                 */
                 export class NestedWebTouchConsumer implements ImportOnTouchListener {
                   listener: (event: TouchEvent) => void = (_) => {}
                   area: Area | undefined = undefined

                   onTouch(id: number, type: number, x: number, y: number, pressure: number, uptimeMillis: number) {
                     const area = this.area
                     if (area === undefined) {
                       return
                     }
                     const eventTarget = new HarkoEventTarget(area)
                     const eventType = type as TouchType
                     const event = new HarkoTouchEvent(type, eventTarget, uptimeMillis, pressure)
                     const touch = new HarkoTouchObject(type, id, x, y, pressure)
                     event.addTouchObject(touch)
                     this.listener(event)
                   }
                 }
                 
                 export class HarkoTouchEvent implements TouchEvent {
                   type: TouchType;
                   touches: TouchObject[] = [];
                   changedTouches: TouchObject[] = [];
                   stopPropagation: () => void = () => {
                   };
                   preventDefault: () => void = () => {
                   };
                   target: EventTarget;
                   timestamp: number;
                   source: SourceType = SourceType.TouchScreen;
                   axisHorizontal?: number | undefined = undefined;
                   axisVertical?: number | undefined = undefined;
                   pressure: number;
                   tiltX: number = 0;
                   tiltY: number = 0;
                   rollAngle?: number | undefined = undefined;
                   sourceTool: SourceTool = SourceTool.Finger;
                   deviceId?: number | undefined = undefined
                   targetDisplayId?: number | undefined = undefined

                   constructor(type: TouchType, target: EventTarget, timestamp: number, pressure: number) {
                     this.type = type;
                     this.target = target;
                     this.timestamp = timestamp;
                     this.pressure = pressure;
                   }

                   addTouchObject(touch: TouchObject) {
                     this.touches.push(touch)
                     this.changedTouches.push(touch)
                   }

                   getHistoricalPoints(): HistoricalPoint[] {
                     return []
                   }

                   getModifierKeyState?(keys: string[]): boolean {
                     return false
                   }
                 }

                 export class HarkoTouchObject implements TouchObject {
                   type: TouchType;
                   id: number;
                   displayX: number;
                   displayY: number;
                   windowX: number;
                   windowY: number;
                   screenX: number;
                   screenY: number;
                   x: number;
                   y: number;
                   hand?: InteractionHand | undefined = undefined;
                   pressedTime?: number | undefined = undefined;
                   pressure?: number | undefined;
                   width?: number | undefined = undefined;
                   height?: number | undefined = undefined;

                   constructor(type: TouchType, id: number, x: number, y: number, pressure: number | undefined) {
                     this.type = type;
                     this.id = id;
                     this.x = x;
                     this.y = y;
                     this.displayX = x;
                     this.displayY = y;
                     this.windowX = x;
                     this.windowY = y;
                     this.screenX = x;
                     this.screenY = y;
                     this.pressure = pressure;
                   }
                 }

                 export class HarkoEventTarget implements EventTarget {
                   area: Area;
                   id?: string | undefined = undefined

                   constructor(area: Area) {
                     this.area = area;
                   }
                 }
                 
            """.trimIndent())
        }
    }

    /**
     * 业务导出的 Compose 代码
     */
    private fun writeExportComposeViews(composeDir: File, extension: BundleHarConfig, indexFile: File) {
        val metaInfo = getMetaInfo()
        if (metaInfo.isNullOrEmpty()) {
            return
        }
        metaInfo.forEach { metaInfo ->
            val file = File(composeDir, "${metaInfo.id}.ets").apply {
                if (exists()) {
                    delete()
                }
                createNewFile()
            }
            file.writer().use {
                val paramType = metaInfo.jsParamType
                val importParamTypeCode = if (metaInfo.isCustomType) {
                    "import { $paramType } from 'lib${extension.soName}.so'"
                } else {
                    ""
                }
                val header = """
                    import { ComposeView, ComposeViewOptions } from "./ComposeView";
                    import { List } from '@kit.ArkTS';
                    $importParamTypeCode

                """.trimIndent()
                val metaCode = "metaData: {composeId: '${metaInfo.id}', hitTestMode: ${metaInfo.hitTestMode}, withInterop: ${metaInfo.withInterop}}"
                val code = if (paramType.isEmpty()) {
                    """
                    @Builder
                    export function ${metaInfo.id}(options: ComposeViewOptions = {}) {
                      ComposeView({$metaCode, param: undefined, options: options})
                    }
                    """.trimIndent()
                } else {
                    """
                    @Builder
                    export function ${metaInfo.id}(param: ${paramType}, options: ComposeViewOptions = {}) {
                      ComposeView({$metaCode, param: param, options: options})
                    }
                    """.trimIndent()
                }
                it.write("$header\n$code")
            }
            indexFile.rewrite {
                "$it\nexport { ${metaInfo.id} } from './src/main/ets/compose/${metaInfo.id}';"
            }
        }
        mutableListOf(
            "initRenderNodeContext",
            "HarkoRenderNode",
            "HarkoChildRenderNode",
            "ShellFrameCaller",
            "RenderFrameCallback",
            "HarkoNodeController",
            "ComposeInitializer",
            "OnBackPressEventBus",
            "OnBackPressCallback",
            "OnDisposeCallback",
            "OnDisposeCollection",
            "SIZE_CONSTRAINT_WRAP_CONTENT",
            "SIZE_CONSTRAINT_MATCH_PARENT",
            "HarkoSizeConstraint",
            "ComposeViewOptions",
            "ComposeViewMetaData",
            "NestedScrollable",
            "NestedWebScrollable",
            "NestedWebTouchConsumer",
            "HarkoTouchEvent",
            "HarkoTouchObject",
            "HarkoEventTarget",
            "renderNodeCApiSupported",
            "setRenderNodeCApiDefaultEnable",
            "AbsHarkoRenderNode",
            "HarkoRenderNodeV2",
            "ContentBuilder",
            "preCompose",
            "disposePreCompose",
        ).apply {
            if (extension.enablePerformanceProbe) {
                add("PreloadHandler")
                add("DefaultPreloadProbe")
            }
        }.forEach { export ->
            indexFile.rewrite {
                "$it\nexport { $export } from './src/main/ets/compose/ComposeView'"
            }
        }
    }

    private fun getMetaInfo(): List<ExportComposableMetaInfo>? {
        val metaInfoFile = File(project.buildDir, META_INFO_PATH)
        if (!metaInfoFile.exists()) {
            return null
        }
        val jsonStr = metaInfoFile.readLines().let {
            it.subList(1, it.size - 1)
        }.joinToString("\n")
        return json.decodeFromString<List<ExportComposableMetaInfo>>(jsonStr)
    }
}

@Serializable
data class ExportComposableMetaInfo(
    val id: String,
    val wrapFunction: DefineMethodInfo,
    val paramTransformFunction: DefineMethodInfo? = null,
    val jsParamType: String = "",
    val isCustomType: Boolean = false,
    val hitTestMode: Int = 0,
    val withInterop: Boolean = false,
)