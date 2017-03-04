import java.util.*;
import java.util.stream.*;
import java.io.*;
import java.math.*;

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 **/
class Player {

    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int width = in.nextInt(); // width of the firewall grid
        int height = in.nextInt(); // height of the firewall grid
        in.nextLine();
        
        // グリッド管理クラス
        WorldModel model = new WorldModel( height, width );
        Search search = new Search();
        for (int i = 0; i < height; i++) {
            String mapRow = in.nextLine(); // one line of the firewall grid

            // グリッド管理クラスの更新
            // i行目 (i: 0 ~ height-1) のmapRowについて，mapRow.charAt(j)は
            // グリッドの (j, i)に相当する (j: 0 ~ width-1)
            model.updateRow( i, mapRow );
        }
        model.debugOut();
        
        // game loop
        long turn = 0;
        List<Field> candidates = Collections.emptyList();
        Bombs bombsActor = new Bombs( turn );
        bombsActor.registerPutBombListener( model );
        bombsActor.registerOnFireListener( model );
        while (true) {
            int rounds = in.nextInt(); // number of rounds left before the end of the game
            int bombs = in.nextInt(); // number of bombs left
            
            if( turn >= 1 )
                model.debugOut();
            candidates = search.calculateFieldsToPutBombsSeq( bombs, model );
            
            Bombs.ActResult actResult = bombsActor.act( turn, candidates, bombs );
            
            turn++;
        }
    }
}

/**
 * 爆弾の設置，管理
 * 爆弾の設置と発火をトリガにしたリスナの起動
 */ 
final class Bombs {
    private final long _initialTurn; //Bombsクラスのコンストラクタで渡される初期ターン
    private final List<Bomb> _placedBombs; //爆弾を置いた場所の履歴
    private final List<PutBombListener> _putBombListeners; //爆弾の設置をトリガに起動するリスナのリスト
    private final List<BombOnFireListener> _onFireListeners; //爆弾の発火をトリガに起動するリスナのリスト
    
    public Bombs( long initialTurn ) {
        this._initialTurn = initialTurn;
        this._placedBombs = new ArrayList<>();
        this._putBombListeners = new ArrayList<>();
        this._onFireListeners = new ArrayList<>();
    }
    
    /**
     * 内部で保持しているBombのリストのうち，
     * turnで発火するものがあれば発火を検知して
     * 保持しているBombOnFireListenerを起動する
     * 
     * @param turn: ターン(0 ~ )
     * @return boolean: 内部で保持しているBombFireListenerが
     *                  1つでも発火すればtrue,
     *                  そうでなければfalse
     */ 
    private boolean triggerIfBombsOnFire( long turn ) {
        boolean isFired = false;
        
        Iterator<Bomb> iterator = this._placedBombs.iterator();
        while( iterator.hasNext() ) {
            Bomb bomb = iterator.next();
            long placedTurn = bomb.placedTurn();
            if( (turn - placedTurn) >= 2 ) {
                for( BombOnFireListener listener : this._onFireListeners ) {
                    listener.onFire( turn, bomb.row(), bomb.col() );
                }
                iterator.remove(); //発火した爆弾は削除する
                isFired = true;
            }
        }
        return isFired;
    }
    
    private boolean hadPutted( Field field ) {
        for( Bomb bomb : this._placedBombs ) {
            if( bomb.row() == field.row() && bomb.col() == field.col() )
                return true;
        }
        return false;
    }

    /**
     * 爆弾を置く予定の場所を受け取って，爆弾を置くかWAITする
     *
     * @param turn: 現在のターン
     * @param toPutOn: 爆弾を置く予定の場所を格納したリスト
     * @param remainderBombs: 残爆弾数
     * @return ActResult: toPutOnのうち，爆弾を置いたFieldと残りのリスト
     *                    以下の時はWAITして，Field.UNDEFINEDとtoPutOnを返す
     *                      remainderBombs <= 0 (残爆弾数が0未満)
     *                      toPutOn.isEmpty()   (爆弾置くべき所がない時)
     *                      toPutOn.get(0).type() != FieldType.EmptyCell ||
     *                        hadPutted( toPutOn.get(0) )
     *                                          (爆弾を置きたい場所がEmptyCellでない，
     *                                           またはそこに既に爆弾を置いてあった)
     */ 
    public ActResult act( long turn, List<Field> toPutOn, int remainderBombs ) {
        assert turn >= this._initialTurn;

        // 爆弾の発火を検知してリスナを起動する
        triggerIfBombsOnFire( turn );
        
        Set<Field> removed = new HashSet<>();
        if( remainderBombs <= 0 ) {
            System.err.printf("Bombs#act(): remainderBombs <= 0 --> doWait(); \n");
            doWait();
            return new ActResult( Field.UNDEFINED, toPutOn );
        }
        if( toPutOn.isEmpty() ) {
            System.err.printf("Bombs#act(): toPutOn.isEmpty() --> doWait(); \n");
            doWait();
            return new ActResult( Field.UNDEFINED, toPutOn );
        }

        //爆弾を置く候補の先頭に爆弾を置く
        //候補の先頭がEmptyCell以外，または既に爆弾が置かれていたらならWAIT
        List<Field> copied = new ArrayList<>( toPutOn );
        Field willPut = copied.get( 0 );
        System.err.printf("Bombs.act(): willPut = %s \n", willPut);
        if( willPut.type() == FieldType.EmptyCell && !hadPutted( willPut ) ) {
            willPut = copied.remove( 0 );
            putBomb( turn, willPut.row(), willPut.col() );
        } else {
            willPut = Field.UNDEFINED;
            doWait();
        }
        
        List<Field> remainder = copied;
        ActResult result = new ActResult( willPut, remainder );
        return result;
    }
    
    /**
     * 爆弾の設置をトリガに起動するリスナ
     * WorldModelにおいて，爆弾が設置された場所から爆風の届く
     * SurveillanceノードをWillFireノードに変更するなどの用途に用いる
     * 
     */ 
    public interface PutBombListener {
        public void onBomb( long turn, int row, int col );
    }
    public void registerPutBombListener( PutBombListener l ) {
        this._putBombListeners.add( l );
    }
    
    /**
     * 爆弾の発火をトリガに起動するリスナ
     * 
     * WorldModelにおいて，爆弾が発火した場所から爆風の届く
     * WillFireノードをEmptyノードに変更するなどの用途に用いる
     */ 
    public interface BombOnFireListener {
        public void onFire( long turn, int row, int col );
    }
    public void registerOnFireListener( BombOnFireListener l ) {
        this._onFireListeners.add( l );
    }
    
    private void putBomb( long turn, int row, int col ) {
        this._placedBombs.add( new Bomb( turn, row, col ) );
        for( PutBombListener l : this._putBombListeners ) {
            l.onBomb( turn, row, col ); //リスナを起動
        }
        System.out.printf("%d %d\n", col, row);
    }
    
    private void doWait() {
        System.out.println("WAIT");
    }

    /**
     * Bombs#act()の返り値で返されるクラス
     */
    public static final class ActResult {
        // act()が呼び出された時に爆弾が設置されたField
        // Waitした場合はField.UNDEFINED
        private final Field _puttedField; 
        // act()に渡された "爆弾を設置したい場所のリスト" のうち
        // act()内で爆弾を設置したFieldを除いた残り
        private final List<Field> _remainder;

        public ActResult( Field field, List<Field> remainder ) {
            this._puttedField = field;
            this._remainder = remainder;
        }

        // Getter達
        public Field puttedField() { return this._puttedField; }
        public List<Field> remainderFieldsList() { return this._remainder; }
    }
    
    /**
     * 爆弾を示すクラス
     * Bomb's'クラスの内部でのみ使う
     * 爆弾を設置した場所とその時の時刻を管理する
     *
     * Bombクラスはコレクションの中に格納されることも考えなければならないため，
     * equals()とhashCode()をオーバライドしている
     * (どのようにオーバライドしたかは下記参考文献を参照)
     *
     * [参考文献]
     * "Effective Java 第2版" 第3章, Joshua Bloch, 柴田 芳樹, 丸善出版
     */
    private static final class Bomb {
        private final long _placedTurn;
        private final int _row;
        private final int _col;
        
        public Bomb( long placedTurn, int row, int col ) {
            this._placedTurn = placedTurn;
            this._row = row;
            this._col = col;
        }
        
        public long placedTurn(){ return this._placedTurn; }
        public int  row(){ return this._row; }
        public int  col(){ return this._col; }
        
        @Override
        public boolean equals( Object o ) {
            if( o == null )
                return false;
            if( o == this )
                return true;
            if( !(o instanceof Bomb) )
                return false;
            Bomb other = (Bomb)o;
            return this._placedTurn == other._placedTurn &&
                   this._row == other._row &&
                   this._col == other._col;
        }
        
        @Override
        public int hashCode() {
            int result = 17;
            result = 37 * result + (int)( this._placedTurn ^ (this._placedTurn >>> 32) );
            result = 37 * result + this._row;
            result = 37 + result + this._col;
            return result;
        }
    }
}

/**
 * フィールドの管理を担うクラス
 * フィールドの生成・更新・取得を提供する
 * フィールド全体はFieldクラスの配列として表現される
 */
final class WorldModel implements Bombs.PutBombListener, Bombs.BombOnFireListener {
    private Field[][] _fields;
    private final int _rows; //グリッドの行個数 
    private final int _cols; //グリッドの列個数 
    
    //===== デバッグのための情報 =====
    private final char _reprs[][]; //ワールドモデルの文字表記
    
    /**
     * @param rows: must be equals to height
     * @param cols: must be equals to width
     */ 
    public WorldModel( int rows, int cols ) {
        assert rows > 2 && rows < 20 && cols > 2 && cols < 20;
        this._rows = rows;
        this._cols = cols;
        this._fields = new Field[rows][cols];
        this._reprs = new char[rows][cols];
        
    }
    
    /**
     *  @param row:  must 0 ~ (height - 1)
     *  @param line: 
     */ 
    public void updateRow( int row, String line ) {
       assert row >= 0 && row < this._rows;
       
       char[] chrs = line.toCharArray();
       assert chrs.length == this._cols;
       
       int col = 0;
       for( char c : chrs ) {
           FieldType type = FieldType.from( c );
           Field field = new Field( type, row, col );
           this._fields[row][col] = field;
           
           // デバッグ情報の蓄積
           this._reprs[row][col] = c;
           
           col++;
       }
    }
    
    /**
     * (row, col)から上下左右3セル分にある
     * 爆弾の設置を受けたフィールドのうち，
     * Passiveノードに遮られていないSurveillance
     * ノードをWillFireノードにする
     * 
     * ロジックはSearch#searchBombedSurveillances()
     * とほぼ同等
     */ 
    @Override
    public void onBomb( long turn, int row, int col ) {
        boolean isUpSearchingAbort = false;
        boolean isLeftSearchingAbort = false;
        boolean isRightSearchingAbort = false;
        boolean isDownSearchingAbort = false;
        
        for( int i = 1; i <= 3; i++ ) {
                
            Field up    = this.up   ( row, col, i );
            Field down  = this.down ( row, col, i );
            Field left  = this.left ( row, col, i );
            Field right = this.right( row, col, i );
            
            if( up.type() == FieldType.Surveillance && !isUpSearchingAbort ) {
                up.surveillanceToWillFire();
                this._reprs[up.row()][up.col()] = up.type().repr();
            } else if( up.type() == FieldType.Passive && !isUpSearchingAbort ) {
                isUpSearchingAbort = true;
            }
            if( down.type() == FieldType.Surveillance && !isDownSearchingAbort ) {
                down.surveillanceToWillFire();
                this._reprs[down.row()][down.col()] = down.type().repr();
            } else if( down.type() == FieldType.Passive && !isDownSearchingAbort ) {
                isDownSearchingAbort = true;
            }
            if( left.type() == FieldType.Surveillance && !isLeftSearchingAbort ) {
                left.surveillanceToWillFire();
                this._reprs[left.row()][left.col()] = left.type().repr();
            } else if( left.type() == FieldType.Passive && !isLeftSearchingAbort ) {
                isLeftSearchingAbort = true;
            }
            if( right.type() == FieldType.Surveillance && !isRightSearchingAbort ) {
                right.surveillanceToWillFire();
                this._reprs[right.row()][right.col()] = right.type().repr();
            } else if( right.type() == FieldType.Passive && !isRightSearchingAbort ) {
                isRightSearchingAbort = true;
            }
        }
        
        System.err.printf("WorldModel#onBomb(): onBomb(%d, %d, %d) \n",
            turn, row, col);
        // this.debugOut();
    }
    
    /**
     * (row, col)から上下左右3セル分にある
     * 爆弾の設置を受けたフィールドのうち，
     * Passiveノードに遮られていないWillFire
     * ノードをEmptyノードにする
     * 
     * ロジックはSearch#searchBombedSurveillances()
     * とほぼ同等
     * 
     */ 
    @Override
    public void onFire( long turn, int row, int col ) {
        boolean isUpSearchingAbort = false;
        boolean isLeftSearchingAbort = false;
        boolean isRightSearchingAbort = false;
        boolean isDownSearchingAbort = false;
        
        for( int i = 1; i <= 3; i++ ) {
                
            Field up    = this.up   ( row, col, i );
            Field down  = this.down ( row, col, i );
            Field left  = this.left ( row, col, i );
            Field right = this.right( row, col, i );
            
            if( up.type() == FieldType.WillFire && !isUpSearchingAbort ) {
                up.willFireToEmpty();
                this._reprs[up.row()][up.col()] = up.type().repr();
            } else if( up.type() == FieldType.Passive && !isUpSearchingAbort ) {
                isUpSearchingAbort = true;
            }
            if( down.type() == FieldType.WillFire && !isDownSearchingAbort ) {
                down.willFireToEmpty();
                this._reprs[down.row()][down.col()] = down.type().repr();
            } else if( down.type() == FieldType.Passive && !isDownSearchingAbort ) {
                isDownSearchingAbort = true;
            }
            if( left.type() == FieldType.WillFire && !isLeftSearchingAbort ) {
                left.willFireToEmpty();
                this._reprs[left.row()][left.col()] = left.type().repr();
            } else if( left.type() == FieldType.Passive && !isLeftSearchingAbort ) {
                isLeftSearchingAbort = true;
            }
            if( right.type() == FieldType.WillFire && !isRightSearchingAbort ) {
                right.willFireToEmpty();
                this._reprs[right.row()][right.col()] = right.type().repr();
            } else if( right.type() == FieldType.Passive && !isRightSearchingAbort ) {
                isRightSearchingAbort = true;
            }
        }
        
        System.err.printf("WorldModel#onFire(): onFire(%d, %d, %d) \n",
            turn, row, col);
        // this.debugOut();
    }
    
    /**
     * @param row: should 0 ~ (this._rows-1)
     * @param col: should 0 ~ (this._cols-1)
     */ 
    public Field here( int row, int col ) {
        if( !isThereField( row, col ) ) {
            return Field.UNDEFINED;
        }
        return this._fields[row][col];
    }
    // ================================================
    // ワールドモデルの各グリッドにアクセスするためのGetter群
    // ================================================
    public Field up   ( int row, int col ) { return this.here( row-1, col   ); }
    public Field left ( int row, int col ) { return this.here( row  , col-1 ); }
    public Field right( int row, int col ) { return this.here( row  , col+1 ); }
    public Field down ( int row, int col ) { return this.here( row+1, col   ); }
    
    public Field up   ( int row, int col, int steps ) { return this.here( row-steps, col   ); }
    public Field left ( int row, int col, int steps ) { return this.here( row      , col-steps ); }
    public Field right( int row, int col, int steps ) { return this.here( row      , col+steps ); }
    public Field down ( int row, int col, int steps ) { return this.here( row+steps, col   ); }
    
    /**
     * ワールドモデルの各グリッドのデバッグ用出力
     */
    public void debugOut() {
        for( int i = 0; i < this._rows; i++ ) {
            for( int j = 0; j < this._cols; j++ ) {
                System.err.printf("%c", this._reprs[i][j]);
            }
            System.err.println();
        }
    }
    
    public void copyFieldsTo( Field[][] dest ) {
        for( int i = 0; i < this._rows; i++ ) {
            for( int j = 0; j < this._cols; j++ ) {
                dest[i][j] = this._fields[i][j].deepCopy();
            }
        }
    }
    
    public int rows() { return this._rows; }
    public int cols() { return this._cols; }
    
    private boolean isThereField( int row, int col ) {
        return row >= 0 && row < this._rows &&
               col >= 0 && col < this._cols;
    }
}

/**
 * 爆弾を設置すべき場所の探索を担う
 * 
 *
 */
final class Search {

    public Search( ) {}
    
    /**
     * 爆弾を設置すべき場所の探索
     * 
     * @param remainingBombs: 残爆弾数
     * @param model: ワールドモデル
     *               内部のField配列をコピーして使う
     *               (わざわざコピーするのは，探索によって
     *                ワールドモデル内部のFieldの状態を変えてしまう
     *                ことを防ぐため)
     */
    public List<Field> calculateFieldsToPutBombsSeq( int remainingBombs, WorldModel model ) {
        Field[][] initialFields = new Field[model.rows()][model.cols()];
        model.copyFieldsTo( initialFields );
        return calculateFieldsToPutBombsSeq( remainingBombs, initialFields, new ArrayList<Field>() );
    }
    
    /**
     * バックトラックによる，爆弾を設置すべき場所のリストの構築
     * 
     * @param remainingBombs:
     * @param fields: 探索する範囲となる，Fieldの2次元配列
     * @param seq: 爆弾を設置すべき場所のリスト
     *             本メソッドの再帰呼び出しによって構築されていく
     */
    private List<Field> calculateFieldsToPutBombsSeq( int remainingBombs, Field[][] fields, List<Field> seq ) {
        // 今fieldsに残っているSurveillanceノードを求める
        Set<Field> surveillances = surveillances( fields );
        
        // 残爆弾数が0 && しかしまだSurveillanceノードが残っている --> 探索失敗
        if( remainingBombs == 0 && surveillances.size() > 0 ) {
            // System.err.printf("Search#calculateFieldsToPutBombsSeq() remainingBombs == 0 && surveillances.size() > 0 \n");
            return Collections.emptyList();
        }
        // Surveillanceノードが0 == 全てのSurveillanceノードが破壊された
        // --> この時の引数seqが "全てのSurveillanceノードを破壊できる" 
        //     爆弾の置き場所のリストなのでそれを返す
        if( surveillances.size() == 0 ) {
            // System.err.printf("Search#calculateFieldsToPutBombsSeq() surveillances.size() == 0 \n");
            return seq;
        } 

        // fieldsに対して，"そこに爆弾を置くことで破壊できるSurveillanceノードの個数"
        // で各fieldをスコア付けしたものを計算する
        ScoredFields scoredFields = calcScoredFields( fields );

        // scoredFields.toSortedList()によって，最も多くSurveillanceノードを破壊
        // できるFieldから順番に爆弾を置く場所の候補を取得して，各候補について
        // バックトラック探索する
        for( Field candidate : scoredFields.toSortedList() ) {
            // 次の一手
            Set<Field> bombed = searchBombedSurveillances( 
                candidate.row(),
                candidate.col(),
                fields
            );
            // fieldsの更新: candidateに爆弾を置いた時に
            //              爆破されるSurveillanceノードを
            //              EmptyCellに変更する
            setFieldsToAfterBombed( 
                candidate.row(),
                candidate.col(),
                bombed,
                fields
            );
            List<Field> nextSeq = new ArrayList<Field>( seq.size() + 1 );
            nextSeq.addAll( seq );
            nextSeq.add( candidate );
            
            // 再帰によるバックトラック探索
            List<Field> recursedSeq = calculateFieldsToPutBombsSeq(
                remainingBombs - 1,
                fields,
                nextSeq
            );

            // fieldsの更新を戻す: candidateに爆弾を置いた時に爆破される
            //                   SurveillanceノードはEmptyCellに変更
            //                   されているので，それをまたSurveillance
            //                   ノードに戻す
            setFieldsToBeforeBombed(
                candidate.row(),
                candidate.col(),
                bombed,
                fields
            );

            // 空リストが返された (すなわち探索失敗) なら，
            // いま着目している爆弾を置く場所の候補(candidate)は除外して
            // 次のループに託す
            if( recursedSeq.isEmpty() ) {
                continue;
            } else {
                for( Field f : recursedSeq ) {
                    System.err.printf("Search#calculateFieldsToPutBombsSeq(): result = %s \n", f);
                }
                // 空リスト以外が返されたら，全てのSurveillanceノードが
                // 破壊できる爆弾の置き場所のリストが返されているので
                // それを呼び出し元に返す
                return recursedSeq;
            }
        }
        // 全部の候補(candidate)を試しても探索失敗
        return Collections.emptyList();
    }
    
    private ScoredFields calcScoredFields( Field[][] fields ) {
        ScoredFields scoredFields = new ScoredFields();
        Set<Field> surveillances = surveillances( fields );
        for( Field surveillance : surveillances ) {
            // fieldsの( surveillance.row(), surveillance.col() )を
            // 破壊できる爆弾の置き場の集合を計算
            Set<Field> candidates = findCandidatePlacesPutBomb (
                surveillance.row(),
                surveillance.col(),
                fields
            );
            // 爆弾の置き場について，Surveillanceを1つ破壊できるので
            // スコア付けをインクリメント
            for( Field candidate : candidates ) {
                scoredFields.incrementScore( candidate );
            }
        }
        
        return scoredFields;
    }
    
    private void setFieldsToAfterBombed( int row, int col, Set<Field> bombed, Field[][] fields ) {
        for( Field surveillance : bombed ) {
            (fields[surveillance.row()][surveillance.col()]).changeTypeTo( FieldType.EmptyCell );
        }
    }
    
    private void setFieldsToBeforeBombed( int row, int col, Set<Field> bombed, Field[][] fields ) {
        for( Field surveillance : bombed ) {
            (fields[surveillance.row()][surveillance.col()]).changeTypeTo( FieldType.Surveillance );
        }
    }
    
    /**
     * @param row: 爆弾を置くと仮定するフィールド(fieldsの行インデックス)
     * @param col: 爆弾を置くと仮定するフィールド(fieldsの列インデックス)
     * @param fields: Fieldの集合
     * @return Set<Field>: bombに爆弾を置いた時に爆破される敵ノード
     *                     の集合(fieldsに含まれているもの)
     */ 
    private Set<Field> searchBombedSurveillances( int row, int col, Field[][] fields ) {
        Set<Field> results = new HashSet<Field>();
        
        // 上方向の探索を打ち切ったかどうかを示すフラグ
        // このフラグは以下のようにセットされる:
        //   ex) 
        //       (r, c) にパッシブノードがいて，(r+1, c)以降に爆弾が置かれた時
        //       --> (r, c)より上に爆風は届かない
        //       --> これ以上上に探索しても敵ノードは爆破されない
        //           ことがわかっているため，探索を打ち切るために
        //           フラグをONにする
        boolean isUpSearchingAbort = false;
        // 右方向の探索を打ち切ったかどうかを示すフラグ
        boolean isRightSearchingAbort = false;
        // 下方向の探索を打ち切ったかどうかを示すフラグ
        boolean isDownSearchingAbort = false;
        // 左方向の探索を打ち切ったかどうかを示すフラグ
        boolean isLeftSearchingAbort = false;
        
        for( int i = 1; i <= 3; i++ ) {
                
            Field up    = this.up   ( row, col, i, fields );
            Field down  = this.down ( row, col, i, fields );
            Field left  = this.left ( row, col, i, fields );
            Field right = this.right( row, col, i, fields );
            
            if( up.type() == FieldType.Surveillance && !isUpSearchingAbort ) {
                results.add( up );
            } else if( up.type() == FieldType.Passive && !isUpSearchingAbort ) {
                isUpSearchingAbort = true;
            }
            if( down.type() == FieldType.Surveillance && !isDownSearchingAbort ) {
                results.add( down );
            } else if( down.type() == FieldType.Passive && !isDownSearchingAbort ) {
                isDownSearchingAbort = true;
            }
            if( left.type() == FieldType.Surveillance && !isLeftSearchingAbort ) {
                results.add( left );
            } else if( left.type() == FieldType.Passive && !isLeftSearchingAbort ) {
                isLeftSearchingAbort = true;
            }
            if( right.type() == FieldType.Surveillance && !isRightSearchingAbort ) {
                results.add( right );
            } else if( right.type() == FieldType.Passive && !isRightSearchingAbort ) {
                isRightSearchingAbort = true;
            }
        }
        
        return results;
    }
    
    private Set<Field> surveillances( Field[][] fields ) {
        Set<Field> surveillances = new HashSet<>();
        for( int i = 0; i < fields.length; i++ ) 
            for( int j = 0; j < fields[i].length; j++ ) 
                if( fields[i][j].type() == FieldType.Surveillance )
                    surveillances.add( fields[i][j] );
        return surveillances;
    }
    
    /**
     * (row, col)に爆風を届けられる爆弾の置き場のリストを返す
     * 
     * @param row:
     * @param col:
     * @return Set<Field>: 爆弾の置き場を示すFieldの集合
     * 
     */ 
    private Set<Field> findCandidatePlacesPutBomb( int row, int col, Field[][] fields ) {
        // (row, col)に爆風の届く範囲のフィールドを得る
        // これが爆弾の置き場の候補となる
        // ((row, col)から上下左右3セル分)
        List<Field> candidates = Arrays.asList(
            this.here( row+3, col  , fields ),
            this.here( row+2, col  , fields ),
            this.here( row+1, col  , fields ),
            this.here( row-1, col  , fields ),
            this.here( row-2, col  , fields ),
            this.here( row-3, col  , fields ),
            this.here( row  , col+3, fields ),
            this.here( row  , col+2, fields ),
            this.here( row  , col+1, fields ),
            this.here( row  , col-1, fields ),
            this.here( row  , col-2, fields ),
            this.here( row  , col-3, fields )
        );
        
        Set<Field> result = new HashSet<>();
        
        for( Field candidate : candidates ) {
            if( candidate.type() == FieldType.EmptyCell ) {
                if( candidate == Field.UNDEFINED )
                    continue;
                if( isThereExistFieldTypeWithin(
                    candidate.row(), candidate.col(), row, col, FieldType.Passive, fields) ) {
                    continue;
                }
                result.add( candidate );
            }
        }
        return result;
    }
    
    private boolean isThereExistFieldTypeWithin( int startRow, 
        int startCol, int endRow, int endCol, FieldType type, Field[][] fields ) {
        
        if( startRow > endRow ) {
            int temp = endRow;
            endRow = startRow;
            startRow = temp;
        }
        if( startCol > endCol ) {
            int temp = endCol;
            endCol = startCol;
            startCol = temp;
        }
        
        for( int i = startRow; i <= endRow; i++ ) {
            for( int j = startCol; j <= endCol; j++ ) {
                if( fields[i][j].type() == type )
                    return true;
            }
        }
        return false;
    }
    
    
    private Field here( int row, int col, Field[][] fields ) {
        if( row >= 0 && row < fields.length && col >= 0 && col < fields[0].length )
            return fields[row][col];
        else 
            return Field.UNDEFINED;
    }
    
    private Field up( int row, int col, int steps, Field[][] fields ) {
        return here( row-steps, col, fields );
    }
    private Field down( int row, int col, int steps, Field[][] fields ) {
        return here( row+steps, col, fields );
    }
    private Field left( int row, int col, int steps, Field[][] fields ) {
        return here( row, col-steps, fields );
    }
    private Field right( int row, int col, int steps, Field[][] fields ) {
        return here( row, col+steps, fields );
    }
    private Field up( int row, int col, Field[][] fields ) {
        return up( row, col, 1, fields );
    }
    private Field down( int row, int col, Field[][] fields ) {
        return down( row, col, 1, fields );
    }
    private Field left( int row, int col, Field[][] fields ) {
        return left( row, col, 1, fields );
    }
    private Field right( int row, int col, Field[][] fields ) {
        return right( row, col, 1, fields );
    }
    
    /**
     * Fieldクラスに対するスコア付けを担うクラス
     * スコア付けと，スコアの降順でソートしたFieldのリストを
     * 提供する
     * 
     */ 
    private class ScoredFields {
        // Fieldとスコアのマップ
        private final Map<Field, Integer> _map;
        
        public ScoredFields() {
            this._map = new HashMap<>();
        }
        
        public void incrementScore( Field field ) {
            if( !this._map.containsKey( field ) ) {
                this._map.put( field, 0 ); //初期スコアを格納する
            }
            int score = this._map.get( field );
            this._map.put( field, score + 1 ); //スコアの加算
        }
        
        public void assignScore( Field field, int assignScore ) {
            this._map.put( field, assignScore );
        }
        
        public List<Field> toSortedList() {
            Set<Field> fieldsSet = this._map.keySet();
            List<Field> fields = new ArrayList<>( fieldsSet.size() );
            fields.addAll( fieldsSet );
            Collections.sort( fields, new Comparator<Field>(){
                @Override
                public int compare( Field f1, Field f2 ) {
                    int score1 = ScoredFields.this._map.get( f1 );
                    int score2 = ScoredFields.this._map.get( f2 );
                    return score2 - score1; //スコアの降順でソートする
                }
            });
            
            return fields;
        }
        
        public int size() {
            return this._map.size();
        }
        
        public Set<Field> fields() {
            return this._map.keySet();
        }
        
        public int score( Field field ) {
            if( this._map.containsKey( field ) ) {
                return this._map.get( field );
            } else {
                throw new IllegalArgumentException("ScoredFields#score(): Map does'nt have key: " + field);
            }
        }
        
        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append( "ScoredFields[ \n");
            for( Map.Entry<Field, Integer> entry : this._map.entrySet() ) {
                Field f = entry.getKey();
                int score = entry.getValue();
                builder.append( String.format( "%s -> %d, \n", f.toString(), score ) );
            }
            builder.append( "]" );
            return builder.toString();
        }
    }
}

/**
 * WorldModel内で
 * セルで区切られた1つの領域を表現する
 */
final class Field {
    private final int _row;  // セルの行インデックス
    private final int _col;  // セルの列インデックス
    private FieldType _type; // セルのタイプ
    
    /**
     * @param type:
     * @param row:
     * @param col:
     */ 
    public Field( FieldType type, 
        int row, int col ) {
    
        this._type = type;
        this._row = row;
        this._col = col;
    }
    
    public FieldType type(){ return this._type; }
    public int row(){ return this._row; }
    public int col(){ return this._col; }
    
    public void changeTypeTo( FieldType type ) {
        this._type = type;
    }
    
    /**
     * このセルのタイプを "見張りノード" から
     * "もうすぐ爆発する" に変更する
     */
    public void surveillanceToWillFire() {
        assert this._type == FieldType.Surveillance;
        this._type = FieldType.WillFire;
    }

    /**
     * このセルのタイプを "もうすぐ爆発する" から
     * "空のセル" に変更する
     */
    public void willFireToEmpty() {
        assert this._type == FieldType.WillFire;
        this._type = FieldType.EmptyCell;
    }
    /**
     * Fieldの深いコピーを返す
     * 
     */ 
    public Field deepCopy() {
        return new Field(
            this._type,
            this._row,
            this._col
        );
    }
    
    @Override
    public String toString() {
        return String.format("Field(%d, %d, %s)",
            this._row, this._col, this._type.toString());
    }
    
    @Override
    public boolean equals(Object o) {
        if( o == null )
            return false;
        if( o == this )
            return true;
        if( !(o instanceof Field) )
            return false;

        Field other = (Field)o;
        return this._row == other._row &&
               this._col == other._row &&
               this._type.equals( other._type ); 
    }
    
    @Override
    public int hashCode() {
        int result = 17;
        result = 37 * result + this._row;
        result = 37 * result + this._col;
        result = 37 + result + this._type.hashCode();
        return result;
    }
    
    public static final Field UNDEFINED = new Field( 
        FieldType.Undefined,
        Integer.MIN_VALUE,
        Integer.MIN_VALUE );
}

enum FieldType {
    //=========================
    // 外部から与えられるフィールド型
    //=========================
    EmptyCell   ( '.' ),
    Surveillance( '@' ),
    Passive     ( '#' ),
    Fire        ( '*' ),
    
    //=========================
    // ユーザ定義のフィールド型
    //=========================
    Undefined   ( '?' ),
    WillFire    ( '+' ),
    Bomb        ( 'b' );
    
    private char _repr;
    private FieldType( char c ) {
        this._repr = c;
    }
    
    public char repr() { return this._repr; }
    
    public static FieldType from( char c ) {
        switch(c) {
            case '.': return FieldType.EmptyCell;
            case '@': return FieldType.Surveillance;
            case '#': return FieldType.Passive;
            case '*': return FieldType.Fire;
            case '+': return FieldType.WillFire;
            case '?': return FieldType.Undefined;
            case 'b': return FieldType.Bomb;
            default : throw new IllegalArgumentException( "Illegal FieldType char : " + c );
        }
    }
}