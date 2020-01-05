# Buildbot
サンプル動画: https://axion014.github.io/content/Buildbot.mp4
プレイヤーをAIで動かし、建築させるMod
導入するとコマンド `/build` が追加される

##### コマンド文法
`/build <<blockname|(blocknames)> [<shape>[(attributes)]]|<filename>> at [(]<x|xmin~xmax>, <y|ymin~ymax>, <z|zmin~zmax>[)]`
- `shape`: `line|plane|box|circle|cylinder`のいずれか
- `blocknames`: コンマ区切りの配列
- `attributes`: コロン/コンマ区切りの属性
  - `axis`: `shape`がcircleかcylinderの時必須。x/y/zのいずれか
  - `radius`: `shape`がcircleかcylinderの時必須。数値
  - `hollow`: 0から3までの整数