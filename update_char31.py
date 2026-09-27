import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3307, user='root', password='Jzuqmy8u@123', database='tsm_dodo', charset='utf8mb4')
with conn.cursor() as cur:
    cur.execute('UPDATE characters SET skill_point = 28, attr_point = 51 WHERE id = 31')
    conn.commit()
    cur.execute('SELECT id, name, level, exp, skill_point, attr_point FROM characters WHERE id = 31')
    row = cur.fetchone()
    print('Updated Char 31:', row)
conn.close()
