import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3307, user='root', password='Jzuqmy8u@123', database='tsm_dodo', charset='utf8mb4')
with conn.cursor() as cur:
    cur.execute('SELECT skill_id, level FROM character_skills WHERE character_id = 31')
    rows = cur.fetchall()
    print('Char 31 skills in DB:', rows)
conn.close()
