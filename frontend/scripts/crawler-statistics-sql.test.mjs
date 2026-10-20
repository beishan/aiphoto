import { after, before, test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { PGlite } from '@electric-sql/pglite'

// Execute the production SQL with PostgreSQL semantics, without requiring a Docker daemon.
const source = readFileSync(new URL('../../backend/src/main/java/com/aiphoto/service/CrawlStatisticsService.java', import.meta.url), 'utf8')
const rawSql = source.match(/static final String SQL = """([\s\S]*?)""";/)[1]
const names = []
const sql = rawSql.replace(/(?<!:):([a-z_]+)/g, (_, name) => {
  if (!names.includes(name)) names.push(name)
  return `$${names.indexOf(name) + 1}`
})
let db
before(async () => {
  db = new PGlite()
  await db.exec(`
    CREATE TABLE crawl_sites (id bigint PRIMARY KEY, owner_id bigint, name text);
    CREATE TABLE crawl_seen_pages (site_id bigint, first_seen_at timestamp);
    CREATE TABLE crawl_jobs (id bigint PRIMARY KEY, owner_id bigint, site_id bigint, phase text, status text, finished_at timestamp);
    CREATE TABLE crawl_pages (job_id bigint, status text, included boolean DEFAULT TRUE, collection_job_id bigint);
    CREATE TABLE crawl_assets (job_id bigint, created_at timestamp, status text, download_started_at_millis bigint, download_duration_millis bigint);
    INSERT INTO crawl_sites VALUES (1, 9, '家庭照片'), (2, 10, '另一个账户');
    INSERT INTO crawl_seen_pages VALUES (1, '2026-10-20 00:00'), (1, '2026-10-19 23:59'), (1, '2026-10-21 00:00'), (2, '2026-10-20 01:00');
    INSERT INTO crawl_jobs VALUES
      (1, 9, 1, 'REVIEW', 'PARTIAL', '2026-10-20 10:00'),
      (2, 10, 2, 'REVIEW', 'COMPLETED', '2026-10-20 10:00'),
      (3, 9, 1, 'DISCOVERY', 'COMPLETED', '2026-10-20 10:00'),
      (4, 9, 1, 'DOWNLOAD', 'RUNNING', NULL),
      (5, 9, 1, 'REVIEW', 'FAILED', '2026-10-20 11:00'),
      (6, 9, 1, 'DOWNLOAD', 'CANCELLED', '2026-10-20 12:00');
    INSERT INTO crawl_pages (job_id, status) VALUES (1, 'SUCCEEDED'), (2, 'SUCCEEDED'), (3, 'PENDING'), (4, 'RUNNING'), (5, 'FAILED'), (6, 'PENDING');
    INSERT INTO crawl_pages VALUES (1, 'PENDING', FALSE, NULL), (1, 'SUCCEEDED', TRUE, 99);
  `)
  const epoch = value => Date.parse(value + '+08:00')
  const image = (job, status, start, duration, created = '2026-10-20 01:00:00') =>
    db.query('INSERT INTO crawl_assets VALUES ($1, $2, $3, $4, $5)', [job, created, status, start == null ? null : epoch(start), duration])
  await image(1, 'DOWNLOADED', '2026-10-20T02:05:00', 1000)
  await image(1, 'FAILED', '2026-10-20T02:05:00', 60000)
  await image(1, 'IMPORTED', '2026-10-20T03:00:00', 4000)
  await image(1, 'DOWNLOADED', null, null) // Legacy records count, without inventing timing samples.
  await image(1, 'SKIPPED', null, null)
  await image(1, 'DOWNLOADED', '2026-10-19T23:59:58', 1000, '2026-10-19 23:00')
  await image(2, 'DOWNLOADED', '2026-10-20T02:05:00', 1000)
  await image(4, 'RUNNING', '2026-10-20T00:00:00', null)
  await image(4, 'PENDING', '2026-10-20T00:00:00', 99999999) // Interrupted old timing must not become a sample.
})
after(async () => { await db?.close() })
async function query(grain = 'day', start = '2026-10-20T00:00:00', end = '2026-10-21T00:00:00', owner = 9, zone = 'Asia/Shanghai') {
  const params = { owner, grain, start, end, zone, source_zone: 'Asia/Shanghai' }
  return (await db.query(sql, names.map(name => params[name]))).rows
}
const sum = (rows, kind, outcome, field = 'amount') => rows.filter(r => r.kind === kind && (outcome == null || r.outcome === outcome)).reduce((n, r) => n + Number(r[field]), 0)

test('SQL isolates account data and counts first discoveries without collection-page copies', async () => {
  const rows = await query()
  assert.equal(sum(rows, 'NEW_PAGE'), 1)
  assert.equal(sum(rows, 'NEW_IMAGE'), 7)
  assert.ok(rows.every(row => Number(row.site_id) === 1))
  const other = await query('day', undefined, undefined, 10)
  assert.equal(sum(other, 'NEW_PAGE'), 1)
  assert.equal(sum(other, 'IMAGE', 'SUCCESS'), 1)
})
test('page results include partial, failed and cancelled; exclude discovery and running jobs', async () => {
  const rows = await query()
  assert.equal(sum(rows, 'PAGE'), 3)
  assert.equal(sum(rows, 'PAGE', 'PARTIAL'), 1)
  assert.equal(sum(rows, 'PAGE', 'FAILED'), 1)
  assert.equal(sum(rows, 'PAGE', 'CANCELLED'), 1)
})
test('finished image counts survive import; historical missing timings do not skew averages', async () => {
  const rows = await query()
  assert.equal(sum(rows, 'IMAGE', 'SUCCESS'), 3)
  assert.equal(sum(rows, 'IMAGE', 'FAILED'), 1)
  assert.equal(sum(rows, 'IMAGE', 'SKIPPED'), 1)
  assert.equal(sum(rows, 'IMAGE', null, 'samples'), 3)
  assert.equal(sum(rows, 'IMAGE', null, 'duration_sum'), 65000)
  assert.equal(sum(rows.filter(r => Number(r.band) === 4), 'IMAGE'), 1)
})
test('hour and minute grouping respect image finish time and application timezone', async () => {
  const hours = await query('hour')
  const images = hours.filter(row => row.kind === 'IMAGE' && Number(row.samples) > 0)
  assert.equal(images.length, 3)
  assert.ok(images.some(row => row.bucket.getHours() === 3 && row.bucket.getDate() === 20))
  const minutes = await query('minute', '2026-10-20T02:05:00', '2026-10-20T02:06:00')
  assert.equal(sum(minutes, 'IMAGE', 'SUCCESS'), 1)
  assert.equal(sum(minutes, 'IMAGE', 'FAILED'), 0) // Failure ended exactly at the exclusive upper boundary.
})
test('grouping converts server-local timestamps into the requested report timezone', async () => {
  const rows = await query('hour', '2026-10-20T00:00:00', '2026-10-21T00:00:00', 9, 'UTC')
  const image = rows.find(row => row.kind === 'IMAGE' && row.outcome === 'SUCCESS' && Number(row.band) === 1)
  assert.equal(image.bucket.getDate(), 19)
  assert.equal(image.bucket.getHours(), 18) // 02:05 in Shanghai finishes in the previous day's 18:00 UTC bucket.
})
test('range is half open and empty ranges return no rows', async () => {
  const rows = await query('day', '2026-10-21T00:00:00', '2026-10-22T00:00:00')
  assert.equal(sum(rows, 'NEW_PAGE'), 1)
  assert.equal(sum(rows, 'IMAGE'), 0)
  assert.deepEqual(await query('minute', '2026-01-01T00:00:00', '2026-01-01T01:00:00'), [])
})

test('task progress aggregate includes all finished image states and excludes pending/running, scoped by owner', async () => {
  const repository = readFileSync(new URL('../../backend/src/main/java/com/aiphoto/repository/CrawlAssetRepository.java', import.meta.url), 'utf8')
  const progressSql = repository.match(/String JOB_IMAGE_PROGRESS_SQL = """([\s\S]*?)""";/)[1].replace(':ownerId', '$1')
  const rows = (await db.query(progressSql, [9])).rows
  const finished = rows.find(row => Number(row.jobId) === 1)
  const running = rows.find(row => Number(row.jobId) === 4)
  assert.equal(Number(finished.total), 6)
  assert.equal(Number(finished.processed), 6)
  assert.equal(Number(running.total), 2)
  assert.equal(Number(running.processed), 0)
  assert.ok(rows.every(row => Number(row.jobId) !== 2))
  const other = (await db.query(progressSql, [10])).rows
  assert.equal(other.length, 1)
  assert.equal(Number(other[0].jobId), 2)
})
