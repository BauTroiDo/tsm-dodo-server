import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3307, user='root', password='Jzuqmy8u@123', database='tsm_dodo', charset='utf8mb4')
with conn.cursor() as cur:
    cur.execute('SELECT id, account_id, name, level FROM characters ORDER BY id DESC LIMIT 10')
    cols = [d[0] for d in cur.description]
    for row in cur.fetchall():
        d = dict(zip(cols, row))
        name = d.get('name', '')
        print('ID:', d.get('id'), 'acc:', d.get('account_id'), 'name:', name.encode('ascii', errors='backslashreplace').decode(), 'hex:', str(name).encode('utf-8').hex())
conn.close()
