import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3307, user='root', password='Jzuqmy8u@123', database='tsm_dodo', charset='utf8mb4')
with conn.cursor() as cur:
    cur.execute('SELECT id, name, level, exp, skill_point, attr_point FROM characters WHERE id = 31')
    row = cur.fetchone()
    cols = [d[0] for d in cur.description]
    d = dict(zip(cols, row))
    d['name'] = d['name'].encode('ascii', errors='backslashreplace').decode()
    print('Char 31 in DB:', d)
conn.close()
