import math._
import scala.util._

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 **/
object Solution extends App {
    val Array(l, h) = for(i <- readLine split " ") yield i.toInt
    
    // Mayanの初期インスタンス生成
    // ( マヤ文字表記1文字あたりの横文字数と
    //   縦文字数をパラメータとする )
    var mayan = Mayan( l, h )
    for(i <- 0 until h) {
        val numeral = readLine
        // 0 ~ 19のマヤ文字表記のi行目を入力
        mayan = mayan.addRepresentationLine( numeral )
    }
    // ここまでで0 ~ 19のマヤ文字表記は全行入力済みなので
    // マヤ文字表記と数字の対応をデコード
    mayan = mayan.decodesRepresentation()
    val s1 = readInt
    for(i <- 0 until s1) {
        val num1line = readLine
        // 計算される1つ目の数字について
        // i行目を入力
        mayan = mayan.addNumLine( num1line )
    }
    // 1つめの数字をデコード
    val (m, num1) = mayan.decodeNumStr()
    mayan = m
    
    val s2 = readInt
    for(i <- 0 until s2) {
        val num2line = readLine
        // 計算される2つ目の数字について
        // i行目を入力
        mayan = mayan.addNumLine( num2line )
    }
    // 2つ目の数字をデコード
    val (m2, num2) = mayan.decodeNumStr()
    mayan = m2
    val operation = readLine
    
    // 1つ目の数字と2つ目の数字について
    // 計算を実行
    val result: Long = operation match {
        case "*" => num1 * num2
        case "/" => num1 / num2
        case "+" => num1 + num2
        case "-" => num1 - num2
    }
    
    // 計算の結果をマヤ文字列にエンコード
    val resultInMayanString = mayan.encodeNum(result)
    
    Console.err.print("Solution#main(): result in mayan string: \n")
    
    // エンコードしたマヤ文字を出力
    for {
        digitInMayanString <- resultInMayanString
        lineInDigit <- digitInMayanString
    } println(lineInDigit)
}

/**
 * クラスMayanのコンパニオンオブジェクト
 * クラスMayanはメソッドの中で，初期化中のパラメータをコンストラクタ引数に入れて
 * インスタンスを次々生成するため，コンストラクタをprivateにしている
 * 
 * クラスMayanのユーザに対するインスタンス生成APIは
 * このコンパニオンオブジェクトのapply()メソッドのみ提供する
 * 
 */ 
object Mayan {
    
    /**
     * @param length: 数字をマヤ文字表記した時の横文字数
     * @param height: 数字をマヤ文字表記した時の縦文字数
     * 
     */ 
    def apply( length: Int, height: Int ): Mayan = {
        new Mayan( length, height )
    }
}

/**
 * @param length: 数字1文字あたりの横文字数
 * @param height: 数字1文字あたりの縦文字数
 * @param representationLines: 各数字(0 ~ 19)の文字列表記
 *                             問題への入力をそのまま格納する
 * @param numLines: 文字列表記された，問題へのオペランドの入力
 *                  問題への入力をそのまま格納する
 * @param reprs: 数字(0~19)と，その文字列表現の組を表したクラスの配列
 */ 
class Mayan private( 
    private val length: Int, 
    private val height: Int, 
    private val representationLines: Array[String] = new Array[String](0),
    private val numLines: Array[String] = new Array[String](0),
    private val reprs: Array[Representation] = new Array[Representation](0) 
) {
    
    /**
     * 数字0 ~ 19のマヤ文字表記を連結したi行目を追加する
     * (i: このメソッドの呼出し回数に依存)
     * 
     * (引数の例)
     *   ".oo.o...oo..ooo.oooo....o...oo..ooo.oooo (...) oooo" ( (...)は中略表記 )
     *   上記は数字0 ~ 19のマヤ文字表記の1行目を全て連結したものである
     *   (問題のRulesに掲載されているマヤ文字表記より)
     * 
     * @param s: 数字0 ~ 19のマヤ文字表記を連結したi行目
     *           sの長さは (マヤ文字1も自分の長さ) * 20でなければならない
     *           (20: 0 ~ 19のマヤ文字表記を連結したものであることから)
     * @return Mayan: representationLinesに引数を連結した新しいMayanインスタンス
     * 
     */ 
    def addRepresentationLine(s: String): Mayan = {
       assert( s.length == this.length * 20 )
       new Mayan( this.length, this.height, (this.representationLines :+ s), this.numLines, this.reprs)
    }
   
    /**
     * 内部に蓄積されたrepresentationLinesを元に
     * クラスRepresentationのインスタンスを生成し
     * パラメタreprsに格納したMayanのインスタンスを返す
     * 
     * representationLinesにはマヤ文字の表記が全て格納されていなければならない
     * 
     * @return パラメタreprsにRepresentationのインスタンスを格納した
     *         新しいMayanインスタンス
     * 
     */ 
    def decodesRepresentation(): Mayan = {
        assert( this.representationLines.length == this.height )
       
        val reprs = for {
            i <- 0 until length * 20 by this.length
            val strs = collectRepresentationStr( i )
            val num = ( i / this.length ).toInt
        } yield Representation( num, strs )
        
        { //===== print for debug =====
            Console.err.println(s"Mayan#decodesRepresentation(): reprs size = ${reprs.length}")
            for( rep <- reprs )
                Console.err.println(s"Mayan#decodesRepresentation(): rep = ${rep}")
        }
        val ret = new Mayan( this.length, this.height, this.representationLines, this.numLines, reprs.toArray )
        ret
    }
    
    /**
     *  数を20進数で分解して文字列表現
     * 
     *  @param num: マヤ文字で表記したい数字
     *  @return Array[Array[String]]: numをマヤ文字表記した配列の配列
     * 
     */ 
    def encodeNum(num: Long): Array[Array[String]] = {
        assert( this.representationLines.length == this.height )
        
        val digitsOnRadix20 = this.radixConvert( num, 20 ) //20進数に基数変換
        
        val encodedForEachDigit: Array[Array[String]] = for {
            digit <- digitsOnRadix20
            repr <- this.reprs
            if repr.isMatch( digit )
        } yield repr.strs
        
        encodedForEachDigit
    }
    
    /**
     * 計算のオペランドとして使われる数字のマヤ文字表記のi行目を入力する
     * (i: このメソッドの呼出回数による)
     * 
     * @param s: マヤ文字表記のi行目
     * @return Mayan: numLinesにsを連結した新しいMayanインスタンス
     * 
     */ 
    def addNumLine(s: String): Mayan = {
        assert( s.length == this.length )
        new Mayan( this.length, this.height, this.representationLines, (this.numLines :+ s), this.reprs )
    }
    
    /**
     * 内部に蓄積されたnumLinesをもとに
     * numLinesが表現する数字を復元して
     * numLinesを空にしたMayanインスタンスとともに返す
     * 
     * numLinesには (マヤ文字表記1文字あたりの縦文字数 * N)の
     * 文字列が格納されていなければならない
     * 
     * @return (Mayan, Long): (numLinesを空にしたインスタンス, 復元された数字)
     * 
     */ 
    def decodeNumStr(): (Mayan, Long) = {
        assert( this.numLines.length % this.height == 0 )
        
        var ret: Long = 0
        // this.numLinesを20進数変換
        for {
            // 各数字の文字列表記について
            i <- 0 until this.numLines.length by this.length 
            // 数字とマヤ文字列表記のペアについて
            repr <- this.reprs
            // 数字(i / this.length)の文字列表記を取り出し
            val digitStrs: Array[String] = extractNumInStr( i )
            
            if repr.isMatch( digitStrs )
        } ret = (ret * 20) + repr.num
        
        ( new Mayan( this.length, this.height, this.representationLines, new Array[String](0), this.reprs ), 
            ret )
    }
    
    /**
     * 基数変換
     * 
     * @param num: 基数変換したい数(10進数表記)
     * @param radix: 変換したい基数
     * @return Array[Long]: numを(radix)進数に変換した
     *                      各桁を格納した配列
     * 
     */ 
    private[this] def radixConvert(num: Long, radix: Int): Array[Long] = {
        val buffer = new scala.collection.mutable.ListBuffer[Long]()

        var n = num
        do {
            val div = n / radix
            val rem = n % radix
            buffer.+=:( rem )
            n = div
        } while( n > 0 )
        buffer.toArray
    }
    
    private[this] def extractNumInStr( start: Int ): Array[String] = {
        ( for {
            i <- start until (start + this.height)
        } yield this.numLines(i) ).toArray
    }
    
    private[this] def collectRepresentationStr( idx: Int ): Array[String] = {
        ( for {
            str <- this.representationLines
        } yield str.substring( idx, idx + this.length ) ).toArray
    }
    
}

/**
 * 数字(0 ~ 19)とマヤ文字のペアを表現するクラス
 * 
 * @param num: 数字
 * @param strs: numを表すマヤ文字
 * 
 */ 
case class Representation(num: Int, strs: Array[String]) {
    
    /**
     * 引数で与えられたマヤ文字が，このクラスで表している
     * マヤ文字と一致するかどうか検査する
     * 
     * @param strs: 検査したいマヤ文字
     * @return マヤ文字の内容が一致すればtrue
     *         そうでなければfalse
     */ 
    def isMatch(strs: Array[String]): Boolean = {
        this.strs.sameElements( strs )
    }
    
    /**
     * 引数で与えられた数字が，このクラスで表している
     * 数字と一致するかどうか検査する
     * 
     * @param n: 検査したい数字
     * @return 数字が一致すればtrue
     *         そうでなければfalse
     * 
     */ 
    def isMatch(n: Int): Boolean = {
        this.num == n
    }
    
    /**
     * 引数で与えられた数字が，このクラスで表している
     * 数字と一致するかどうか検査する
     * 
     * @param n: 検査したい数字
     * @return 数字が一致すればtrue
     *         そうでなければfalse
     * 
     */ 
    def isMatch(n: Long): Boolean = {
        this.num == n
    }
    
    override def toString(): String = {
        val formattedStrsArr = for {
            str <- this.strs
        } yield s"${str}\n"
        
        val builder = new scala.collection.mutable.StringBuilder()
        builder.append(s"Representation(${num}, [ \n")
        
        for( s <- formattedStrsArr )
            builder.append(s)
        builder.append("] )\n")
        builder.toString()
        
        
    }
}