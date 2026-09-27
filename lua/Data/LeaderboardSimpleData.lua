LeaderboardSimpleData = {};
LeaderboardSimpleData.__index = LeaderboardSimpleData;
local this = LeaderboardSimpleData;

EBoardKind = {
  ActivityModel = 1,
}

this.ActivityKind = 10; --模板版型10
local selfData = {};
local selfDataIndex = 1;
local remoteData = {};
local groupIds = {};

--[[
  S:126-000 <分組排行榜> +榜數量(4) +<<[排行榜資料]>>
  [排行榜資料] = 排行榜Kind(1) +排行榜ID(2) +[組別排行榜資料]
  [組別排行榜資料] = 組別ID(4) +名次數量(4) +<<[單筆排名顯示資料]>>
  [單筆排名顯示資料] = 玩家ID(8) +n(1) +玩家名(n) +分數(4)

  C:126-001 <問分組排行榜目前分數> +排行榜Kind(1) +排行榜ID(2)
]]--
function LeaderboardSimpleData.ReceiveData(data)
  local boardCount = data:ReadUInt32();
  for i = 1, boardCount do
    local kind = data:ReadByte();
    local boardId = data:ReadUInt16();
    local groupId = data:ReadUInt32();
    local groupSize = data:ReadUInt32();
    remoteData[kind] = remoteData[kind] or {};
    remoteData[kind][boardId] = remoteData[kind][boardId] or {};
    remoteData[kind][boardId].players = remoteData[kind][boardId].players or {};
    if groupSize ~= 0 then
      for j = 1, groupSize do
        local playerId = data:ReadInt64();
        local name = data:ReadString(data:ReadByte());
        local score = data:ReadUInt32();
        remoteData[kind][boardId].players[j] = {};
        remoteData[kind][boardId].players[j].playerId = playerId;
        remoteData[kind][boardId].players[j].playerName = name;
        remoteData[kind][boardId].players[j].playerScore = score;
        if playerId == Role.playerId then
          local found = false;
          for _, v in pairs(selfData) do
            if v.groupId == groupId and v.boardId == boardId and v.boardKind == kind then
              v.playersIndex = j;
              found = true;
              break;
            end
          end

          if not found then
            selfData[selfDataIndex] = {};
            selfData[selfDataIndex].groupId = groupId;
            selfData[selfDataIndex].boardId = boardId;
            selfData[selfDataIndex].boardKind = kind;
            selfData[selfDataIndex].playersIndex = j;
            selfDataIndex = selfDataIndex + 1;
          end
        end
      end
      if groupIds[kind] == nil then
          groupIds[kind] = {};
      end
      groupIds[kind][boardId] = groupId;
    end
  end
end

function LeaderboardSimpleData.UpdateSelfScore(boardKind, boardId, score)
  local board = this.GetSelfBoard(boardKind, boardId);

  if table.Count(board) > 0 then
    for _, v in pairs(board.players) do
      if v.playerId == Role.playerId then
        v.playerScore = score;
        break;
      end
    end
  end
  board.score = score;
end

function LeaderboardSimpleData.GetSelfBoard(boardKind, boardId)
  return remoteData[boardKind][boardId];
end

function LeaderboardSimpleData.GetRank(boardKind, boardId, rank)
  local board = this.GetSelfBoard(boardKind, boardId);
  if table.Count(board) > 0 then
    for i = 1, table.Count(board.players) do
      if board.players[i].playerId == Role.playerId and rank == nil then
        return i;
      elseif rank then
        return board.players[rank];
      end
    end
    return 0;
  else
    return 0;
  end
end

function LeaderboardSimpleData.GetSelfScore(boardKind, boardId)
  local board = this.GetSelfBoard(boardKind, boardId);
  if table.Count(board) > 0 then
    for _, v in pairs(board.players) do
      if v.playerId == Role.playerId then
        return v.playerScore;
      end
    end

    if board.score then
      return board.score;
    else
      return 0;
    end
  else
    return 0;
  end
end

function LeaderboardSimpleData.GetBoardPlayersCount(boardKind, boardId)
  local board = this.GetSelfBoard(boardKind, boardId);
  if table.Count(board) > 0 then
    local players = board.players;
    return table.Count(players);
  else
    return 0;
  end
end

function LeaderboardSimpleData.GetRankReward(boardKind, boardId)
  if boardKind == EBoardKind.ActivityModel then
    this.GetActivityModelReward(boardId);
  end
end

function LeaderboardSimpleData.GetActivityModelReward(boardId)
    sendBuffer:Clear();
    sendBuffer:WriteByte(ActivityModel.openActivityId);
    sendBuffer:WriteByte(ESpecialActivity.LeaderBoard);
    sendBuffer:WriteInt32(1); --領獎
    Network.Send(124, 5, sendBuffer);
end

function LeaderboardSimpleData.Clear()
  table.Clear(remoteData);

  selfData = {};
  selfDataIndex = 1;
end