package com.colheitagps.app

import android.content.Context
import android.graphics.Path
import android.graphics.PointF
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import org.locationtech.jts.geom.*
import java.io.File
import kotlin.math.*

/** Extract text and cartographic paths locally. A raster-only PDF is left for manual selection. */
internal class PdfCurveReader(page:PDPage,private val enabled:(String)->Boolean={true}):PDFGraphicsStreamEngine(page) {
    data class Label(val text:String,val at:Coordinate)
    data class Outline(val layer:String,val points:List<Coordinate>,val red:Boolean,val black:Boolean,val group:Int=0)
    val labels=mutableListOf<Label>();val outlines=mutableListOf<Outline>()
    private val height=page.cropBox.height.toDouble()
    private val left=page.cropBox.lowerLeftX.toDouble();private val bottom=page.cropBox.lowerLeftY.toDouble()
    private val layers=mutableListOf<String>()
    private var paths=mutableListOf<MutableList<Coordinate>>()
    private var current=mutableListOf<Coordinate>()
    private var rawPoint=PointF()
    private var glyphText=StringBuilder();private var glyphStart:Coordinate?=null;private var glyphEnd:Coordinate?=null
    private var glyphCenter:Coordinate?=null;private var glyphSize=0.0
    private var operations=0
    private var drawing=0
    private fun point(x:Float,y:Float)=Coordinate(x-left,height-(y-bottom))
    private fun guard() {check(++operations<500000) {"Mapa complexo demais para leitura automática neste aparelho."};check(!Thread.currentThread().isInterrupted)}
    override fun beginMarkedContentSequence(tag:COSName?,properties:COSDictionary?) {
        val dictionary=properties?:try {resources.getProperties(tag)?.cosObject} catch(_:Exception) {null}
        layers.add(dictionary?.getString(COSName.NAME)?:layers.lastOrNull().orEmpty())
    }
    override fun endMarkedContentSequence() {if(layers.isNotEmpty()) layers.removeAt(layers.lastIndex)}
    override fun moveTo(x:Float,y:Float) {guard();if(current.isNotEmpty()) paths.add(current);current=mutableListOf(point(x,y));rawPoint=PointF(x,y)}
    override fun lineTo(x:Float,y:Float) {guard();current.add(point(x,y));rawPoint=PointF(x,y)}
    override fun curveTo(x1:Float,y1:Float,x2:Float,y2:Float,x3:Float,y3:Float) {
        guard();val a=PointF(rawPoint.x,rawPoint.y)
        for(i in 1..16) {val t=i/16f;val u=1-t;current.add(point(u*u*u*a.x+3*u*u*t*x1+3*u*t*t*x2+t*t*t*x3,u*u*u*a.y+3*u*u*t*y1+3*u*t*t*y2+t*t*t*y3))};rawPoint=PointF(x3,y3)
    }
    override fun getCurrentPoint()=rawPoint
    override fun closePath() {if(current.isNotEmpty()) {current.add(Coordinate(current.first()));rawPoint=PointF((current.first().x+left).toFloat(),(height-current.first().y+bottom).toFloat())}}
    override fun appendRectangle(p0:PointF,p1:PointF,p2:PointF,p3:PointF) {moveTo(p0.x,p0.y);lineTo(p1.x,p1.y);lineTo(p2.x,p2.y);lineTo(p3.x,p3.y);closePath()}
    override fun drawImage(image:PDImage?) {guard()}
    override fun clip(windingRule:Path.FillType?) {}
    override fun endPath() {paths.clear();current.clear()}
    private fun store(stroked:Boolean) {
        drawing++
        if(current.isNotEmpty()) paths.add(current)
        val rgb=try {graphicsState.strokingColor.toRGB()} catch(_:Exception) {0x777777}
        val red=((rgb shr 16) and 255)>180&&((rgb shr 8) and 255)<40&&(rgb and 255)<40
        val black=((rgb shr 16) and 255)<40&&((rgb shr 8) and 255)<40&&(rgb and 255)<40
        if(stroked&&layers.all(enabled)) for(path in paths) if(path.size>1) outlines.add(Outline(layers.lastOrNull().orEmpty(),path.toList(),red,black,drawing))
        paths=mutableListOf();current=mutableListOf()
    }
    override fun strokePath()=store(true)
    override fun fillPath(windingRule:Path.FillType?)=store(false)
    override fun fillAndStrokePath(windingRule:Path.FillType?)=store(true)
    override fun shadingFill(name:COSName?) {}
    override fun showGlyph(matrix:Matrix,font:PDFont,code:Int,displacement:Vector) {
        guard();val text=font.toUnicode(code).orEmpty();val start=point(matrix.translateX,matrix.translateY)
        if(!layers.all(enabled)) {flushText();return}
        val end=matrix.transformPoint(displacement.x,displacement.y);val center=matrix.transformPoint(displacement.x/2,.3f)
        val size=matrix.scalingFactorX.toDouble().coerceAtLeast(1.0)
        if(text.isBlank()) {flushText();return}
        if(glyphEnd?.distance(start)?.let {it>max(size,glyphSize)*.65}!=false) flushText()
        if(glyphStart==null) glyphStart=start
        glyphText.append(text);glyphEnd=point(end.x,end.y);glyphSize=size
        val c=point(center.x,center.y);glyphCenter=glyphStart?.let {Coordinate((it.x+glyphEnd!!.x)/2+(c.x-(start.x+glyphEnd!!.x)/2),(it.y+glyphEnd!!.y)/2+(c.y-(start.y+glyphEnd!!.y)/2))}
    }
    fun flushText() {
        val text=glyphText.toString().trim()
        if(text.matches(Regex("[0-9]{1,5}([,.][0-9]{1,4})?"))) glyphCenter?.let {labels.add(Label(text,it))}
        glyphText=StringBuilder();glyphStart=null;glyphEnd=null;glyphCenter=null
    }
    companion object {
        data class Result(val width:Double,val height:Double,val labels:List<Label>,val paths:List<Outline>)
        fun read(context:Context,file:File):Result {
            require(file.length() in 1..40_000_000) {"Use um PDF de até 40 MB para o reconhecimento de curvas."}
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(file,com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(12L*1024*1024).setTempDir(context.cacheDir)).use {document->
                require(document.numberOfPages>0)
                val page=document.getPage(0)
                require(page.rotation==0&&abs(page.cropBox.lowerLeftX)<.01&&abs(page.cropBox.lowerLeftY)<.01) {"Este PDF precisa de conferência manual de orientação antes da seleção de curvas."}
                val oc=document.documentCatalog.ocProperties
                val reader=PdfCurveReader(page) {name->name.isBlank()||oc?.getGroup(name)==null||oc.isGroupEnabled(name)};reader.processPage(page);reader.flushText()
                return Result(page.cropBox.width.toDouble(),page.cropBox.height.toDouble(),reader.labels,reader.outlines)
            }
        }
    }
}
