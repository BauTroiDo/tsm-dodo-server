import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3307, user='root', password='Jzuqmy8u@123', database='tsm_dodo', charset='utf8mb4')
with conn.cursor() as cur:
    cur.execute('SELECT skill_point, attr_point FROM characters WHERE id = 31')
    print('Char 31 points:', cur.fetchone())
conn.close()
