Lottery = {}
local this = Lottery;

--const
this.maxNumberCount = 5; --需選號碼數
this.maxNumber = 42;

--var
this.award = 0; --總獎金
this.tickets = {}; --已下注彩券

function Lottery.AddTicket(data)
  local ticket = {}
  for i = 1, this.maxNumberCount do
    ticket[i] = data:ReadByte();
  end

  table.insert(this.tickets, ticket);
end