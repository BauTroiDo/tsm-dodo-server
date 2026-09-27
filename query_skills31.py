import pymysql
conn = pymysql.connect(host='127.0.0.1', port=3307, user='root', password='Jzuqmy8u@123', database='tsm_dodo', charset='utf8mb4')
with conn.cursor() as cur:
    cur.execute('SELECT * FROM character_skills LIMIT 1')
    cols = [d[0] for d in cur.description]
    print('Columns in character_skills:', cols)
    id_col = [c for c in cols if 'id' in c.lower()][0]
    cur.execute(f'SELECT * FROM character_skills WHERE {id_col} = 31')
    rows = cur.fetchall()
    print('Skills for char 31:')
    for r in rows:
        print(' ', dict(zip(cols, r)))
conn.close()
