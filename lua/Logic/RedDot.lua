ERedDot = {
  Mail = 1, --信件
  TeamInvitation = 2, --隊伍邀請
  AttributePoint = 3, --屬性點數
  MountLevel = 4, --坐騎升級
  MountAttribute = 5, --坐騎屬性
  MountCollect = 6, --坐騎收集
  LogainAward = 7, --每日登入獎勵
  Achievement = 8, --成就系統
  FriendInvitation = 9, --好友邀請
  SkillPoint = 10, --技能點數
  Bag = 11, --背包
  Online = 12, --上線獎勵
  OpenServer = 13, --開服獎勵
  DungeonInvitation = 14, --副本邀請
  OrgInvitation = 15, --軍團邀請
  Dungeon = 16, --副本房間
  Save = 17, --儲值送
  Dispatch = 18, --派遣
  Jiugongge = 19, --九宮格
  Whisper = 20, --私訊
  Back = 21, --消費利返
  OffLineExp = 22,  --離線經驗值
  OrgMemberInvitation = 23, --軍團申請
  CollectWarrior = 24, --收集冊武將
  CollectStyle = 25, --收集冊時裝
  CollectCard = 26, --收集冊卡片組合
  Change = 27, --好禮兌換
  CollectCardSort = 28, --收集冊卡片
  Festival = 29, --慶典
  Mall = 30, --商城
  FriendAdd = 31; --好友新增
  FriendGift = 32; --好友送禮
  OrgSign = 33, --軍團簽到
  Consume = 34, --消費送
  LottoNpc = 35, --抽抽樂武將
  LottoCard = 36, --抽抽樂卡片
  Prize_1 = 37, --prize1活動
  Prize_2 = 38, --prize2活動
  Prize_3 = 39, --prize3活動
  Prize_4 = 40, --prize4活動
  Prize_5 = 41, --prize5活動
  Prize_6 = 42, --prize6活動
  OrgBoss = 43, --軍團BOSS
  DragonBox = 44, --藏龍寶箱
  OrgProduction = 45, --軍團製造
  SupportArmy = 46, --支援軍團
  MoneyTree = 47, --搖錢樹
  DoubleSave = 48, --儲值雙倍送
  Furnace = 49, --黃金熔爐
  Scratch = 50, --儲值刮刮卡
  LimitedTimeSale = 51, --限時購物
  ConsumeScratch = 52, --消費刮刮卡
  Poke = 53, --戳戳樂送
  StagePass = 54, --百戰天關
  ClassAward = 55, --達標領獎
  ActLogin = 56, --累積登入領獎
  ActAccLogin = 57, --連續登入領獎
  ReturnLogin = 58, --老手回歸
  Swimming = 59, --泳池獎勵
  ReUseAct_1 = 60, --重覆使用1(one RoleCount)
  ReUseAct_2 = 61, --重覆使用2(one RoleCount)
  ExpBottle = 62, --經驗瓶
  Collect = 63, --全服收集
  Arena = 64, --周年競技慶典
  ReUseAct_3 = 65, --重覆使用3
  ReUseAct_4 = 66, --重覆使用4
  ReUseAct_5 = 67, --重覆使用5
  ReUseAct_6 = 68, --重覆使用6
  ArmyLuckyBag = 69, --軍團福帶
  PurchaseFree = 70,
  ServerLuckyBag = 71, --伺服器紅包
  CharmReward = 72,
  GiftFeedback = 73,
  Breakthrough = 74, --等級突破
  OtherHouse = 75, --其他小屋
  BattlePass = 82, --通行證
  ArmySupportInfo = 86,  --支援戰
  ArmySupportPoke = 87,  --支援戰戳戳樂
  ActivityModelHasAct = 88, --活動模板
  VIP = 89, --VIP
}

RedDot = {};
local this = RedDot;

local redDots = {};

function RedDot.Clear()
  table.Clear(redDots);
end

function RedDot.Add(kind)
  redDots[kind] = true;

  this.UpdateUI(kind);
end

function RedDot.Remove(kind)
  redDots[kind] = nil;

  this.UpdateUI(kind);
end

function RedDot.UpdateUI(kind)
  Function.UpdateRedDot();
  UIFrame.UpdateRedDot();
end

function RedDot.Check(...)
  for k, v in pairs({...}) do
    if redDots[v] ~= nil then
      return true;
    end
  end

  return false;
end