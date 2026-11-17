import { before, after, test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { PGlite } from '@electric-sql/pglite'

const schema = readFileSync(new URL('../../backend/src/main/resources/schema.sql', import.meta.url), 'utf8')
const repository = readFileSync(new URL('../../backend/src/main/java/com/aiphoto/repository/CategoryRepository.java', import.meta.url), 'utf8')
const exclusionSchema = schema.match(/CREATE TABLE IF NOT EXISTS photo_tag_exclusions\s*\([\s\S]*?\);/)[0] + schema.match(/CREATE INDEX IF NOT EXISTS idx_photo_tag_exclusions_tag[^;]*;/)[0]
const manual = repository.match(/@Query\(value = "(INSERT INTO photo_categories[^"\n]+)"/)[1]
const remove = repository.match(/@Query\(value = "(DELETE FROM photo_categories WHERE category_id[^"\n]+)"/)[1]
const clearAuto = repository.match(/DELETE FROM photo_categories\s+WHERE category_id = :categoryId AND source = 'auto'/)[0]
const sql = (query, photo = 10) => query.replaceAll(':categoryId', '1').replaceAll(':photoId', String(photo))
let db
before(async () => {
  db = new PGlite()
  await db.exec(`CREATE TABLE photos(id bigint PRIMARY KEY); CREATE TABLE tags(id bigint PRIMARY KEY);
    CREATE TABLE categories(id bigint PRIMARY KEY);
    CREATE TABLE photo_categories(category_id bigint REFERENCES categories(id), photo_id bigint REFERENCES photos(id), source text DEFAULT 'auto', added_at timestamp, PRIMARY KEY(category_id, photo_id));
    INSERT INTO photos VALUES(10),(11),(12); INSERT INTO tags VALUES(1),(2); INSERT INTO categories VALUES(1);
    ${exclusionSchema}
    INSERT INTO photo_categories(category_id,photo_id,source) VALUES(1,10,'auto'),(1,11,'auto');`)
})
after(async () => db?.close())
test('manual confirmation survives auto cleanup and remains idempotent', async () => {
  await db.exec(sql(manual))
  await db.exec(sql(manual))
  await db.exec(sql(clearAuto))
  assert.deepEqual((await db.query('SELECT photo_id::int, source FROM photo_categories ORDER BY photo_id')).rows, [{ photo_id: 10, source: 'manual' }])
})
test('removing one membership does not demote or rewrite other manual links', async () => {
  await db.exec(sql(manual, 12))
  await db.exec(sql(remove))
  await db.exec(sql(clearAuto))
  assert.deepEqual((await db.query('SELECT photo_id::int, source FROM photo_categories')).rows, [{ photo_id: 12, source: 'manual' }])
})
test('exclusions enforce resource foreign keys and cascade on tag deletion', async () => {
  await assert.rejects(db.exec('INSERT INTO photo_tag_exclusions VALUES(999,1)'))
  await db.exec('INSERT INTO photo_tag_exclusions VALUES(10,1); DELETE FROM tags WHERE id=1;')
  assert.equal((await db.query('SELECT * FROM photo_tag_exclusions')).rows.length, 0)
  await db.exec(exclusionSchema)
})
