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
        
        WorldModel model = new WorldModel( height, width );
        Search search = new Search( model );
        for (int i = 0; i < height; i++) {
            String mapRow = in.nextLine(); // one line of the firewall grid
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
            
             candidates = search.decidePlacesPutBomb( bombs );
            
            // 爆弾を置く候補 (candidates) に爆弾を1つ置く
            // candidatesはFieldのリストとして構築され，そのFieldに爆弾を置いた時
            // 破壊できるSurveillanceノードの数の降順でソートされている
            // 爆弾が置かれた時，返り値は爆弾の置かれた場所の集合(要素数1) FIXME
            // Bombs#act()内で，爆弾が置かれたことをトリガにして
            // その爆弾に破壊されるSurveillanceノードがEmptyノードに変更される
            Field hadBePutted = bombsActor.act( turn, candidates, bombs );
            if( hadBePutted != Field.UNDEFINED ) {
                // 爆破後に残ったSurveillanceノードに対して
                // 再度爆弾を置く候補を探索し直す
                candidates = search.decidePlacesPutBomb( bombs-1 ); 
            }
            turn++;
        }
    }
}

/**
 * 爆弾の管理を担うクラス
 * 
 * 
 */ 
final class Bombs {
    private final long _initialTurn;
    private final List<Bomb> _placedBombs;
    private final List<PutBombListener> _putBombListeners;
    private final List<BombOnFireListener> _onFireListeners;
    
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
    
    /**
     * 
     * @param turn: 現在のターン
     * @param toPutOn: 爆弾を置く予定の場所を格納したリスト
     *                 破壊できるSurveillanceノードが多い順のリストになっていること
     * @param remainderBombs: 残爆弾数
     * @return Field: toPutOnのうち，爆弾を置いたField
     *                以下の時はWAITしてField.UNDEFINEDを返す
     *                  remainderBombs <= 0 (残爆弾数が0未満)
     *                  toPutOn.isEmpty()   (爆弾置くべきときがない時)
     */ 
    public Field act( long turn, List<Field> toPutOn, int remainderBombs ) {
        assert turn >= this._initialTurn;
        
        Set<Field> removed = new HashSet<>();
        if( remainderBombs <= 0 ) {
            System.err.printf("Bombs#act(): remainderBombs <= 0 --> doWait(); \n");
            doWait();
            return Field.UNDEFINED;
        }
        if( toPutOn.isEmpty() ) {
            System.err.printf("Bombs#act(): toPutOn.isEmpty() --> doWait(); \n");
            doWait();
            return Field.UNDEFINED;
        }
        // 爆弾の発火を検知してリスナを起動する
        triggerIfBombsOnFire( turn );

        //爆弾を置く候補の先頭(候補のうち，最も多くSurveillanceノードを爆破できる所)
        Field willPut = toPutOn.get( 0 );
        putOnBomb( turn, willPut.row(), willPut.col() );
        
        return willPut;
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
    
    private void putOnBomb( long turn, int row, int col ) {
        this._placedBombs.add( new Bomb( turn, row, col ) );
        for( PutBombListener l : this._putBombListeners ) {
            l.onBomb( turn, row, col ); //リスナを起動
        }
        System.out.printf("%d %d\n", col, row);
    }
    
    private void doWait() {
        System.out.println("WAIT");
    }
    
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
    private final int _rows; //グリッドの行個数 (0 ~  width-1)
    private final int _cols; //グリッドの列個数 (0 ~ height-1)
    
    //===== キャッシュのための情報 =====
    private final Set<Field> _surveillances;
    private final Set<Field> _passives;
    
    //===== デバッグのための情報 =====
    private final char _reprs[][];
    
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
        
        this._surveillances = new HashSet<Field>();
        this._passives = new HashSet<Field>();
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
           
           // 情報のキャッシュ
           if( type == FieldType.Surveillance ) {
               this._surveillances.add( field );
           }
           if( type == FieldType.Passive ) {
               this._passives.add( field );
           }
           
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
     * 
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
                this._surveillances.remove( up );
                up.surveillanceToWillFire();
                this._reprs[up.row()][up.col()] = up.type().repr();
            } else if( up.type() == FieldType.Passive && !isUpSearchingAbort ) {
                isUpSearchingAbort = true;
            }
            if( down.type() == FieldType.Surveillance && !isDownSearchingAbort ) {
                this._surveillances.remove( down );
                down.surveillanceToWillFire();
                this._reprs[down.row()][down.col()] = down.type().repr();
            } else if( down.type() == FieldType.Passive && !isDownSearchingAbort ) {
                isDownSearchingAbort = true;
            }
            if( left.type() == FieldType.Surveillance && !isLeftSearchingAbort ) {
                this._surveillances.remove( left );
                left.surveillanceToWillFire();
                this._reprs[left.row()][left.col()] = left.type().repr();
            } else if( left.type() == FieldType.Passive && !isLeftSearchingAbort ) {
                isLeftSearchingAbort = true;
            }
            if( right.type() == FieldType.Surveillance && !isRightSearchingAbort ) {
                this._surveillances.remove( right );
                right.surveillanceToWillFire();
                this._reprs[right.row()][right.col()] = right.type().repr();
            } else if( right.type() == FieldType.Passive && !isRightSearchingAbort ) {
                isRightSearchingAbort = true;
            }
        }
        
        System.err.printf("WorldModel#onBomb(): onBomb(%d, %d, %d) \n",
            turn, row, col);
        this.debugOut();
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
                this._surveillances.remove( up );
                up.willFireToEmpty();
                this._reprs[up.row()][up.col()] = up.type().repr();
            } else if( up.type() == FieldType.Passive && !isUpSearchingAbort ) {
                isUpSearchingAbort = true;
            }
            if( down.type() == FieldType.WillFire && !isDownSearchingAbort ) {
                this._surveillances.remove( down );
                down.willFireToEmpty();
                this._reprs[down.row()][down.col()] = down.type().repr();
            } else if( down.type() == FieldType.Passive && !isDownSearchingAbort ) {
                isDownSearchingAbort = true;
            }
            if( left.type() == FieldType.WillFire && !isLeftSearchingAbort ) {
                this._surveillances.remove( left );
                left.willFireToEmpty();
                this._reprs[left.row()][left.col()] = left.type().repr();
            } else if( left.type() == FieldType.Passive && !isLeftSearchingAbort ) {
                isLeftSearchingAbort = true;
            }
            if( right.type() == FieldType.WillFire && !isRightSearchingAbort ) {
                this._surveillances.remove( right );
                right.willFireToEmpty();
                this._reprs[right.row()][right.col()] = right.type().repr();
            } else if( right.type() == FieldType.Passive && !isRightSearchingAbort ) {
                isRightSearchingAbort = true;
            }
        }
        
        System.err.printf("WorldModel#onFire(): onFire(%d, %d, %d) \n",
            turn, row, col);
        this.debugOut();
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
    
    public Field up   ( int row, int col ) { return this.here( row-1, col   ); }
    public Field left ( int row, int col ) { return this.here( row  , col-1 ); }
    public Field right( int row, int col ) { return this.here( row  , col+1 ); }
    public Field down ( int row, int col ) { return this.here( row+1, col   ); }
    
    public Field up   ( int row, int col, int steps ) { return this.here( row-steps, col   ); }
    public Field left ( int row, int col, int steps ) { return this.here( row      , col-steps ); }
    public Field right( int row, int col, int steps ) { return this.here( row      , col+steps ); }
    public Field down ( int row, int col, int steps ) { return this.here( row+steps, col   ); }
    
    public void debugOut() {
        for( int i = 0; i < this._rows; i++ ) {
            for( int j = 0; j < this._cols; j++ ) {
                System.err.printf("%c", this._reprs[i][j]);
            }
            System.err.println();
        }
    }
    
    public Set<Field> surveillances() { return this._surveillances; }
    public Set<Field> passives() { return this._passives; }
    
    public int rows() { return this._rows; }
    public int cols() { return this._cols; }
    
    private boolean isThereField( int row, int col ) {
        return row >= 0 && row < this._rows &&
               col >= 0 && col < this._cols;
    }
}

/**
 * 探索を担うクラス
 * ワールドモデルを元にして，爆弾を置く候補の探索を提供する
 * 
 */ 
final class Search {
    
    private final WorldModel _model;
    
    public Search( WorldModel model ) {
        this._model = model;
    }
    
    /**
     * 
     * @return 爆弾を置くべきと判断したFieldのリスト
     *         リストは「要素に含まれるFieldに爆弾を置いた時に
     *         爆破できる敵ノードの数」の降順でソートされている
     */ 
    public List<Field> decidePlacesPutBomb( int remainingBombs ) {
        long start = System.currentTimeMillis();
        // 見張りノードの集合
        Set<Field> surveillances = this._model.surveillances();
        System.err.printf("Search#decidePlacesPutBomb(): num of surveillances = %d \n",
            surveillances.size());
        
        // 爆破できる敵ノードの数でスコア付けされた
        // 爆弾を置くフィールドの候補(集合)
        Search.ScoredFields places = new Search.ScoredFields();
        
        for( Field surveillance : surveillances ) {
            // 敵ノードの位置(sr, sc)に対して，(sr, sc)に
            // 爆風を届けられるフィールドの候補集合を取得する
            Set<Field> candidates = findCandidatePlacesPutBomb( 
                surveillance.row(), 
                surveillance.col()
            );
            for( Field candidate : candidates ) {
                places.incrementScore( candidate );
            }
        }
        // 上のfor文が終わると，変数placesには
        // 全Surveillanceノードに対してそこを爆破する置き場の全候補が入っている
        System.err.printf("Search#decidePlacesPutBoms(): candidates size = %d \n", places.size());
        
        Set<Field> alreadyBombedSurveillances = new HashSet<>();
        // 爆破できる敵ノードの数でスコア付けされた，爆弾を置くフィールド
        // の候補をスコアの降順でソートしたリスト
        List<Field> sortedList = places.toSortedList();        
        Iterator<Field> iterator = sortedList.iterator();
        while( iterator.hasNext() ) {
            Field bomb = iterator.next();
            Set<Field> bombedSurveillances = searchBombedSurveillances( bomb );
            // 場所bombに爆弾を置いて爆破できるSurveillanceノード全てが
            // 既に破壊予定のものであった場合，場所bombに爆弾を置いても
            // 新たな敵ノードを破壊できるわけではないので爆弾を置く候補から削除する
            if( alreadyBombedSurveillances.containsAll( bombedSurveillances ) ) {
                iterator.remove();
            } else {
                bombedSurveillances.removeAll( alreadyBombedSurveillances );
                places.assignScore( bomb, bombedSurveillances.size() );
                alreadyBombedSurveillances.addAll( bombedSurveillances );
            }
        }
        sortedList = places.toSortedList();
        long end = System.currentTimeMillis();
        System.err.printf( "Search#decidePlacesPutBomb(): process time = %d ms\n", (end - start) );
        
        { //-- debug out --
            for( Field field : sortedList ) {
                System.err.printf("Search#decidePlacesPutBomb(): debug: %s -> %d \n",
                    field, places.score( field ) );
            }
        }
        return sortedList;
    }
    

    private static final SearchNode ERROR_NODE = 
        new SearchNode( 
            Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Collections.emptySet() );
    private static class SearchNode {
        private final int _row;
        private final int _col;
        private int _remainingBombs;
        private Set<Field> _remainingSurveillances;
        private SearchNode _ancestor = ERROR_NODE;
        public SearchNode( int r, int c, int b, Set<Field> rem ) {
            this._row = r; this._col = c; this._remainingBombs = b;
            this._remainingSurveillances = rem;
        }
        public void setAncestor( SearchNode node ) {
            this._ancestor = node;
        }
        public int row() { return this._row; }
        public int col() { return this._col; }
        public SearchNode ancestor() { return this._ancestor; }
        public Set<Field> remainingSurveillances() { return this._remainingSurveillances; }
        public int remainingBombs() { return this._remainingBombs; }
        
        @Override
        public boolean equals( Object o ) {
            if( o == null )
                return false;
            if( o == this )
                return true;
            if( !(o instanceof SearchNode) )
                return false;
            SearchNode other = (SearchNode)o;
            return this._row == other._row &&
                   this._col == other._col &&
                   this._remainingBombs == other._remainingBombs &&
                   this._ancestor.equals( other._ancestor ) &&
                   this._remainingSurveillances.equals( other._remainingSurveillances );
        }
        
        @Override
        public int hashCode() {
            int result = 17;
            result = result * 37 + this._row;
            result = result * 37 + this._col;
            result = result * 37 + this._remainingBombs;
            result = result * 37 + this._ancestor.hashCode();
            result = result * 37 + this._remainingSurveillances.hashCode();
            return result;
        }
    }
    /**
     * @param bomb: 爆弾を置くと仮定するフィールド
     * @return Set<Field>: bombに爆弾を置いた時に爆破される敵ノード
     *                     の集合
     */ 
    private Set<Field> searchBombedSurveillances( Field bomb ) {
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
                
            Field up    = this._model.up   ( bomb.row(), bomb.col(), i );
            Field down  = this._model.down ( bomb.row(), bomb.col(), i );
            Field left  = this._model.left ( bomb.row(), bomb.col(), i );
            Field right = this._model.right( bomb.row(), bomb.col(), i );
            
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
    

    /**
     * (row, col)に爆風を届けられる爆弾の置き場のリストを返す
     * 
     * @param row:
     * @param col:
     * @return Set<Field>: 爆弾の置き場を示すFieldの集合
     * 
     */ 
    private Set<Field> findCandidatePlacesPutBomb( int row, int col ) {
        assert row >= 0 && row < this._model.rows() &&
               col >= 0 && col < this._model.cols() ;
               
        // (row, col)に爆風の届く範囲のフィールドを得る
        // ((row, col)から上下左右3セル分)
        List<Field> candidates = Arrays.asList(
            this._model.here( row+3, col   ),
            this._model.here( row+2, col   ),
            this._model.here( row+1, col   ),
            this._model.here( row-1, col   ),
            this._model.here( row-2, col   ),
            this._model.here( row-3, col   ),
            this._model.here( row  , col+3 ),
            this._model.here( row  , col+2 ),
            this._model.here( row  , col+1 ),
            this._model.here( row  , col-1 ),
            this._model.here( row  , col-2 ),
            this._model.here( row  , col-3 )
        );
        
        Set<Field> result = new HashSet<>();
        
        // 探索を無視する方向のリスト
        // 以下for文内で更新され，探索の打切りによる処理効率化に使われる
        Set<Direction> discardsDirs = new HashSet<>();
        
        for( Field candidate : candidates ) {
            Field forCheckPassiveNode = Field.UNDEFINED;
            // 爆弾を置くことが出来る場所ならば
            if( candidate.type() == FieldType.EmptyCell ) {
                // 爆弾を置くことができても，パッシブノードにさえぎられて
                // (row, col)に爆風が届かないかもしれない
                // --> 爆弾を置くことが出来る場所から(row, col)までの間に
                //     パッシブノードがあるならば，爆風は届かないので
                //     そこは除外する
                
                // 爆弾を置く候補が (row, col)より上側にある && 上側の探索はまだ打切られていない
                if( candidate.row() < row && !discardsDirs.contains( Direction.Up ) ) {
                    // 爆弾を置く候補は(row, col)より上側にある
                    // --> 候補より下側にパッシブノードがあるか探索する
                    for( int step = 1; step < (row - candidate.row()); step++ ) {
                        forCheckPassiveNode = this._model.down( row, col, step );
                        if( forCheckPassiveNode.type() == FieldType.Passive ) {
                            // これ以降は，(row, col)より上方向への探索はしなくて良い
                            discardsDirs.add( Direction.Up );
                            break;
                        }
                    }
                // 爆弾を置く候補が (row, col)より下側にある && 下側の探索はまだ打切られていない
                } else if( candidate.row() > row && !discardsDirs.contains( Direction.Down ) ) {
                    // 爆弾を置く候補は(row, col)より下側にある
                    // --> 候補より上側にパッシブノードがあるか探索する
                    for( int step = 1; step < (candidate.row() - row); step++ ) {
                        forCheckPassiveNode = this._model.up( row, col, step );
                        if( forCheckPassiveNode.type() == FieldType.Passive ) {
                            // これ以降は，(row, col)より右方向への探索はしなくて良い
                            discardsDirs.add( Direction.Down );
                            break;
                        }
                    }
                // 爆弾を置く候補が (row, col)より左側にある && 左側の探索はまだ打切られていない
                } else if( candidate.col() < col && !discardsDirs.contains( Direction.Left ) ) {
                    // 爆弾を置く候補は(row, col)より左側にある
                    // --> 候補より右側にパッシブノードがあるか探索する
                    for( int step = 1; step < (col - candidate.col()); step++ ) {
                        forCheckPassiveNode = this._model.right( row, col, step );
                        if( forCheckPassiveNode.type() == FieldType.Passive ) {
                            // これ以降は，(row, col)より左方向への探索はしなくて良い
                            discardsDirs.add( Direction.Left );
                            break;
                        }
                    }
                // 爆弾を置く候補が (row, col)より右側にある && 右側の探索はまだ打切られていない
                } else if( candidate.col() > col && !discardsDirs.contains( Direction.Right ) ) {
                    // 爆弾を置く候補は(row, col)より右側にある
                    // --> 候補より左側にパッシブノードがあるか探索する
                    for( int step = 1; step < (candidate.col() - col); step++ ) {
                        forCheckPassiveNode = this._model.left( row, col, step );
                        if( forCheckPassiveNode.type() == FieldType.Passive ) {
                            // これ以降は，(row, col)より右方向への探索はしなくて良い
                            discardsDirs.add( Direction.Right );
                            break;
                        }
                    }
                } else {
                }
                
                // forCheckPassiveNodeがField.UNDEFINEDのまま
                // --> 爆弾を置く場所の候補から(row, col)までの間にパッシブノードはない
                //     ので，候補のリストに追加する
                if( forCheckPassiveNode == Field.UNDEFINED )
                    result.add( candidate );
            }
        }
        return result;
    }
    
    private enum Direction {
        Up, Left, Right, Down
    }
    /**
     * Fieldクラスに対するスコア付けを担うクラス
     * スコア付けと，スコアの降順でソートしたFieldのリストを
     * 提供する
     * 
     */ 
    private class ScoredFields {
        // Fieldとスコアのマップ
        // Map<Field, Integer>の形式だと
        // 値のボクシング・アンボクシングが多く発生するため
        // int型で要素数1の配列でスコアを表現する
        private final Map<Field, int[]> _map;
        
        public ScoredFields() {
            this._map = new HashMap<>();
        }
        
        public void incrementScore( Field field ) {
            if( !this._map.containsKey( field ) ) {
                int[] score = new int[1];
                score[0] = 0; //初期スコアを格納する
                this._map.put( field, score );
            }
            int[] score = this._map.get( field );
            score[0] = score[0] + 1; //スコアの加算
        }
        
        public void assignScore( Field field, int assignScore ) {
            if( !this._map.containsKey( field ) ) {
                int[] score = new int[1];
                this._map.put( field, score );
            }
            int[] score = this._map.get( field );
            score[0] = assignScore;
        }
        
        public List<Field> toSortedList() {
            Set<Field> fieldsSet = this._map.keySet();
            List<Field> fields = new ArrayList<>( fieldsSet.size() );
            fields.addAll( fieldsSet );
            Collections.sort( fields, new Comparator<Field>(){
                @Override
                public int compare( Field f1, Field f2 ) {
                    int score1 = ScoredFields.this._map.get( f1 )[0];
                    int score2 = ScoredFields.this._map.get( f2 )[0];
                    return score2 - score1; //スコアの降順でソートする
                }
            });
            
            System.err.println();
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
                return this._map.get( field )[0];
            } else {
                throw new IllegalArgumentException("ScoredFields#score(): Map does'nt have key: " + field);
            }
        }
    }
}

final class Field {
    private final int _row;
    private final int _col;
    private FieldType _type;
    
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
    
    
    public void surveillanceToWillFire() {
        assert this._type == FieldType.Surveillance;
        this._type = FieldType.WillFire;
    }
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