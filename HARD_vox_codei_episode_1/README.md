# Vox Codei - Episode 1

## 問題へのリンク

[Vox Codei](https://www.codingame.com/training/hard/vox-codei-episode-1)

## 問題の感触

貫通式ボムを持ったボンバーマンみたいなやつ

## 解法

* バックトラックによる探索 ([1] 第7章より)
    * ある爆弾の置き場所の候補を試して，手を進めて，ダメなら戻る
    * 手の先読みなしでもいける
* ちょっとした工夫
    * 爆弾の置き場所の候補を列挙する時に，もっとも敵ノード(Surveillanceノード)を
    破壊できる候補から順に探索する
        * あまり可能性の低い候補から探索しても時間がかかるので，
        可能性の有りそうなところから探索することで時間短縮を図る

## 参考文献

[1] 「アルゴリズム設計マニュアル 上巻」, S.S.スキーナ, 平田富夫, 丸善出版