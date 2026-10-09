@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.duobudget.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.duobudget.app.BuildConfig
import com.duobudget.app.model.*
import com.duobudget.app.ui.theme.DuoBudgetTheme
import com.duobudget.app.viewmodel.AppViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.UUID

private val moneyFormat=NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).apply{minimumFractionDigits=0;maximumFractionDigits=2}
private fun rub(n:Long)=moneyFormat.format(BigDecimal.valueOf(n,2))+" ₽"
private val dates=DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
private fun typeName(t:TransactionType)=when(t){TransactionType.EXPENSE->"Расход";TransactionType.INCOME->"Доход";TransactionType.TRANSFER->"Перевод";TransactionType.REFUND->"Возврат";TransactionType.CASHBACK->"Кэшбэк"}
private fun syncLabel(s:SyncState)=when(s){SyncState.DISABLED->"На телефоне";SyncState.READY->"Ожидание";SyncState.SYNCING->"Обновление…";SyncState.SYNCED->"Обновлено";SyncState.ERROR->"Нет связи"}

@Composable
fun DuoBudgetApp(openExpenseRequest:Int=0,unlocked:Boolean=true,vm:AppViewModel=viewModel()) {
    val dark by vm.darkTheme.collectAsStateWithLifecycle()
    val error by vm.storageError.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner,unlocked){
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_RESUME)vm.setForeground(unlocked)else if(event==Lifecycle.Event.ON_STOP)vm.setForeground(false) }
        owner.lifecycle.addObserver(observer)
        vm.setForeground(unlocked&&owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose{owner.lifecycle.removeObserver(observer);vm.setForeground(false)}
    }
    DuoBudgetTheme(darkTheme=dark){
        if(error.isNotBlank()){
            Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                Text("Восстановление бюджета",style=MaterialTheme.typography.headlineSmall)
                Text(error)
                BackupPanel(vm,recovery=true)
            }
        } else {
            val nav=rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val route=entry?.destination?.route?:"home"
            val tabs=listOf(Triple("home","Главная","⌂"),Triple("history","Операции","≡"),Triple("budget","Бюджет","◫"),Triple("savings","Цели","◇"),Triple("settings","Ещё","⋯"))
            LaunchedEffect(openExpenseRequest){if(openExpenseRequest>0)nav.navigate("entry"){launchSingleTop=true}}
            Scaffold(
                modifier=Modifier.testTag("app"),
                bottomBar={if(route in tabs.map{it.first})NavigationBar{tabs.forEach{t->NavigationBarItem(selected=route==t.first,onClick={nav.navigate(t.first){popUpTo("home"){saveState=true};launchSingleTop=true;restoreState=true}},icon={Text(t.third)},label={Text(t.second)})}}}
            ){padding->
                Box(Modifier.fillMaxSize().padding(padding)){
                    NavHost(nav,startDestination="home"){
                        composable("home"){HomeScreen(vm,{nav.navigate("entry")},{nav.navigate("entry/INCOME")},{nav.navigate("accounts")},{nav.navigate("settings")})}
                        composable("history"){HistoryScreen(vm){nav.navigate("edit/"+it)}}
                        composable("budget"){BudgetScreen(vm)}
                        composable("savings"){SavingsScreen(vm)}
                        composable("settings"){SettingsScreen(vm){nav.navigate("accounts")}}
                        composable("accounts"){AccountsScreen(vm){nav.popBackStack()}}
                        composable("entry"){TransactionForm(vm,null,TransactionType.EXPENSE){nav.popBackStack()}}
                        composable("entry/{type}"){e->TransactionForm(vm,null,runCatching{TransactionType.valueOf(e.arguments?.getString("type").orEmpty())}.getOrDefault(TransactionType.EXPENSE)){nav.popBackStack()}}
                        composable("edit/{id}"){e->
                            val data by vm.data.collectAsStateWithLifecycle()
                            val t=data.transactions.firstOrNull{it.id==e.arguments?.getString("id")}
                            if(t==null)Column(Modifier.padding(24.dp)){Text("Операция не найдена");TextButton(onClick={nav.popBackStack()}){Text("Назад")}}
                            else TransactionForm(vm,t,t.type){nav.popBackStack()}
                        }
                    }
                    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
        if(message.isNotBlank())AlertDialog(onDismissRequest=vm::clearMessage,title={Text("DuoBudget")},text={Text(message)},confirmButton={TextButton(onClick=vm::clearMessage){Text("Понятно")}})
    }
}
@Composable private fun Title(text:String){Text(text,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
@Composable private fun MoneyField(value:String,onChange:(String)->Unit,label:String,zero:Boolean=false,negative:Boolean=false,modifier:Modifier=Modifier){
    val parsed=Money.parse(value)
    val invalid=value.isNotBlank()&&(parsed==null||(!negative&&parsed<0)||(!zero&&parsed==0L))
    OutlinedTextField(value,{onChange(it.take(64))},label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=invalid,
        supportingText=if(invalid)({Text("Введите сумму до 1 млрд ₽, не более двух знаков после запятой")})else null,modifier=modifier.fillMaxWidth())
}
@Composable private fun MonthPicker(vm:AppViewModel,month:YearMonth){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
        TextButton(onClick={vm.setMonth(month.minusMonths(1))},modifier=Modifier.semantics{contentDescription="Предыдущий месяц"}){Text("‹")}
        Text(month.format(DateTimeFormatter.ofPattern("LLLL uuuu",Locale.forLanguageTag("ru-RU"))).replaceFirstChar{it.titlecase()},fontWeight=FontWeight.Medium)
        TextButton(onClick={vm.setMonth(month.plusMonths(1))},enabled=month<YearMonth.now(),modifier=Modifier.semantics{contentDescription="Следующий месяц"}){Text("›")}
    }
}
@Composable private fun Metric(label:String,value:Long,modifier:Modifier=Modifier){
    Card(modifier){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
        Text(rub(value),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=if(value<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }}
}
@Composable private fun Empty(text:String){Card(Modifier.fillMaxWidth()){Text(text,Modifier.padding(18.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}}

@Composable private fun HomeScreen(vm:AppViewModel,onExpense:()->Unit,onIncome:()->Unit,onAccounts:()->Unit,onSettings:()->Unit){
    val d by vm.data.collectAsStateWithLifecycle();val month by vm.month.collectAsStateWithLifecycle();val sync by vm.syncState.collectAsStateWithLifecycle()
    val expenses=BudgetEngine.netExpenses(d,month);val income=BudgetEngine.incomes(d,month);val available=BudgetEngine.available(d)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item {Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Title("Семейный бюджет");Text(d.myName+" + "+d.partnerName,color=MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(onClick=onSettings){Text(syncLabel(sync))}}}
        item{MonthPicker(vm,month)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("Доходы",income,Modifier.weight(1f));Metric("Расходы − возвраты",expenses,Modifier.weight(1f))}}
        item{Metric("Доступно сейчас",available,Modifier.fillMaxWidth())}
        item{Text("Остатки всех счетов за вычетом накоплений и целей.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Лимит месяца",fontWeight=FontWeight.SemiBold)
            if(d.budget>0){
                LinearProgressIndicator(progress={(expenses.toFloat()/d.budget).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                Text(rub(expenses)+" из "+rub(d.budget))
                Text(if(expenses>d.budget)"Превышение: "+rub(expenses-d.budget) else "Осталось: "+rub(d.budget-expenses),color=if(expenses>d.budget)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if(month==YearMonth.now()){val today=LocalDate.now();val days=today.lengthOfMonth()-today.dayOfMonth+1;Text("На день до конца месяца: "+rub((d.budget-expenses).coerceAtLeast(0)/days))}
            }else Text("Задай лимит во вкладке «Бюджет»")
            if(month==YearMonth.now()&&expenses>0){val today=LocalDate.now();val projection=(BigDecimal.valueOf(expenses)*BigDecimal.valueOf(today.lengthOfMonth().toLong())/BigDecimal.valueOf(today.dayOfMonth.toLong())).toLong();Text("Прогноз расходов: "+rub(projection),style=MaterialTheme.typography.bodySmall)}
        }}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick=onExpense,modifier=Modifier.weight(1f).height(52.dp)){Text("+ Расход")};OutlinedButton(onClick=onIncome,modifier=Modifier.weight(1f).height(52.dp)){Text("+ Доход")}}}
        item{OutlinedButton(onClick=onAccounts,modifier=Modifier.fillMaxWidth()){Text("Счета и начальные остатки")}}
        item{Metric("Всего накоплено, включая цели",BudgetEngine.savingsTotal(d),Modifier.fillMaxWidth())}
        item{Text("Последние операции",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        val recent=d.transactions.filterNot{it.deleted}.sortedByDescending{it.createdAt}.take(5)
        if(recent.isEmpty())item{Empty("Начни с остатков счетов и добавь первый доход или расход.")}
        items(recent,key={it.id}){t->TransactionCard(d,t)}
    }
}
@Composable private fun TransactionCard(d:BudgetData,t:MoneyTransaction,onEdit:(()->Unit)?=null,onDelete:(()->Unit)?=null,onRestore:(()->Unit)?=null){
    val prefix=when(t.type){TransactionType.EXPENSE->"−";TransactionType.TRANSFER->"↔ ";else->"+"}
    Card(Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(t.category,fontWeight=FontWeight.SemiBold);Text(typeName(t.type),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text(prefix+rub(t.amount),fontWeight=FontWeight.Bold)}
            if(t.note.isNotBlank())Text(t.note,maxLines=4,overflow=TextOverflow.Ellipsis)
            val from=d.accounts.firstOrNull{it.id==t.accountId}?.name.orEmpty()
            val to=d.accounts.firstOrNull{it.id==t.targetAccountId}?.name.orEmpty()
            Text(from+(if(t.type==TransactionType.TRANSFER)" → "+to else "")+" · "+(if(t.payer==Payer.ME)d.myName else d.partnerName),style=MaterialTheme.typography.bodySmall)
            Text(t.createdAt.format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(onEdit!=null||onDelete!=null||onRestore!=null)Row(Modifier.align(Alignment.End)){
                onEdit?.let{TextButton(onClick=it){Text("Изменить")}};onDelete?.let{TextButton(onClick=it){Text("Удалить")}};onRestore?.let{TextButton(onClick=it){Text("Восстановить")}}
            }
        }
    }
}
@Composable private fun HistoryScreen(vm:AppViewModel,onEdit:(String)->Unit){
    val d by vm.data.collectAsStateWithLifecycle();val month by vm.month.collectAsStateWithLifecycle()
    var trash by rememberSaveable{mutableStateOf(false)};var allMonths by rememberSaveable{mutableStateOf(false)};var query by rememberSaveable{mutableStateOf("")};var payer by remember{mutableStateOf<Payer?>(null)}
    var deleting by remember{mutableStateOf<MoneyTransaction?>(null)}
    val rows=d.transactions.filter{it.deleted==trash&&(allMonths||YearMonth.from(it.createdAt)==month)&&(payer==null||it.payer==payer)&&listOf(it.category,it.note,vm.payerName(it.payer)).any{s->s.contains(query.trim(),true)}}.sortedByDescending{it.createdAt}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Title(if(trash)"Корзина"else"Операции")};TextButton(onClick={trash=!trash}){Text(if(trash)"К истории"else"Корзина")}}}
        item{MonthPicker(vm,month)}
        item{OutlinedTextField(query,{query=it.take(80)},label={Text("Поиск по категории, имени, заметке")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(allMonths,{allMonths=!allMonths},label={Text("Все месяцы")});FilterChip(payer==null,{payer=null},label={Text("Все")});FilterChip(payer==vm.myPayer,{payer=vm.myPayer},label={Text("Я")});FilterChip(payer!=null&&payer!=vm.myPayer,{payer=if(vm.myPayer==Payer.ME)Payer.PARTNER else Payer.ME},label={Text("Партнёр")})}}
        if(rows.isEmpty())item{Empty(if(trash)"Здесь нет удалённых операций"else"Операций по этому фильтру пока нет")}
        items(rows,key={it.id}){t->val editable=vm.canEdit(t);TransactionCard(d,t,onEdit=if(editable&&!trash)({onEdit(t.id)})else null,onDelete=if(editable&&!trash)({deleting=t})else null,onRestore=if(editable&&trash)({vm.restoreTransaction(t.id)})else null)}
    }
    deleting?.let{t->AlertDialog(onDismissRequest={deleting=null},title={Text("Удалить операцию?")},text={Text(t.category+" · "+rub(t.amount)+". Её можно восстановить из корзины.")},confirmButton={TextButton(onClick={vm.deleteTransaction(t.id);deleting=null}){Text("Удалить")}},dismissButton={TextButton(onClick={deleting=null}){Text("Отмена")}})}
}
@Composable private fun TransactionForm(vm:AppViewModel,old:MoneyTransaction?,initialType:TransactionType,onDone:()->Unit){
    val d by vm.data.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    var type by rememberSaveable(old?.id){mutableStateOf(old?.type?:initialType)}
    var amount by rememberSaveable(old?.id){mutableStateOf(old?.amount?.let(Money::edit).orEmpty())}
    var note by rememberSaveable(old?.id){mutableStateOf(old?.note.orEmpty())}
    var category by rememberSaveable(old?.id){mutableStateOf(old?.category?:vm.categories.first())}
    var account by rememberSaveable(old?.id){mutableStateOf(old?.accountId?:d.accounts.first{!it.archived}.id)}
    var target by rememberSaveable(old?.id){mutableStateOf(old?.targetAccountId?.ifBlank{null}?:d.accounts.firstOrNull{!it.archived&&it.id!=account}?.id.orEmpty())}
    var payer by rememberSaveable(old?.id){mutableStateOf(old?.payer?:vm.myPayer)}
    var dateText by rememberSaveable(old?.id){mutableStateOf((old?.createdAt?.toLocalDate()?:LocalDate.now()).format(dates))}
    var custom by rememberSaveable{mutableStateOf(false)}
    val date=runCatching{LocalDate.parse(dateText,dates)}.getOrNull()
    val validDate=date!=null&&date<=LocalDate.now()&&date.year>=1900
    val parsed=Money.parse(amount)
    val choices=(vm.categories+d.categoryLimits.keys+d.transactions.filter{it.type==TransactionType.EXPENSE||it.type==TransactionType.REFUND}.map{it.category}).distinct()
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Title(if(old==null)"Новая операция"else"Изменить операцию")}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){TransactionType.entries.forEach{t->FilterChip(type==t,{type=t},label={Text(typeName(t))})}}}
        item{MoneyField(amount,{amount=it},"Сумма, ₽")}
        item{OutlinedTextField(dateText,{dateText=it.take(10)},label={Text("Дата · ДД.ММ.ГГГГ")},singleLine=true,isError=!validDate,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),supportingText=if(!validDate)({Text("Введите существующую дату, не позднее сегодня")})else null,modifier=Modifier.fillMaxWidth())}
        if(type==TransactionType.EXPENSE||type==TransactionType.REFUND){
            item{Text("Категория",fontWeight=FontWeight.SemiBold)}
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){choices.forEach{c->FilterChip(category==c&&!custom,{category=c;custom=false},label={Text(c)})};FilterChip(custom,{custom=!custom},label={Text("Своя категория")})}}
            if(custom)item{OutlinedTextField(category,{category=it.take(60)},label={Text("Название категории")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        }
        item{Text(if(type==TransactionType.EXPENSE)"Кто оплатил"else"Участник",fontWeight=FontWeight.SemiBold)}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Payer.entries.forEach{p->FilterChip(payer==p,{payer=p},label={Text(vm.payerName(p))})}}}
        item{Text(if(type==TransactionType.TRANSFER)"Откуда перевод"else"Счёт",fontWeight=FontWeight.SemiBold)}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){d.accounts.filter{!it.archived||it.id==old?.accountId}.forEach{a->FilterChip(account==a.id,{account=a.id},label={Text(a.name)})}}}
        if(type==TransactionType.TRANSFER){
            item{Text("Куда перевод",fontWeight=FontWeight.SemiBold)}
            item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){d.accounts.filter{it.id!=account&&(!it.archived||it.id==old?.targetAccountId)}.forEach{a->FilterChip(target==a.id,{target=a.id},label={Text(a.name)})}}}
            if(d.accounts.count{!it.archived}<2)item{Empty("Для перевода добавь второй счёт в разделе «Ещё → Счета».")}
        }
        item{OutlinedTextField(note,{note=it.take(500)},label={Text("Заметка")},modifier=Modifier.fillMaxWidth(),maxLines=4)}
        item{Button(onClick={vm.saveTransaction(old,type,parsed!!,category,note,account,target,payer,date!!.atTime(old?.createdAt?.toLocalTime()?:LocalTime.now()),onDone)},
            enabled=!busy&&parsed!=null&&parsed>0&&validDate&&category.isNotBlank()&&(type!=TransactionType.TRANSFER||(target.isNotBlank()&&target!=account))&&(old==null||vm.canEdit(old)),modifier=Modifier.fillMaxWidth().height(52.dp)){Text("Сохранить операцию")}}
        item{OutlinedButton(onClick=onDone,modifier=Modifier.fillMaxWidth()){Text("Отмена")}}
    }
}
@Composable private fun BudgetScreen(vm:AppViewModel){
    val d by vm.data.collectAsStateWithLifecycle();val month by vm.month.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    var value by rememberSaveable(d.budget){mutableStateOf(if(d.budget==0L)""else Money.edit(d.budget))}
    var category by rememberSaveable{mutableStateOf(vm.categories.first())}
    var limit by rememberSaveable(category,d.categoryLimits){mutableStateOf(d.categoryLimits[category]?.let(Money::edit).orEmpty())}
    val parsed=if(value.isBlank())0L else Money.parse(value)
    val parsedLimit=if(limit.isBlank())0L else Money.parse(limit)
    val categories=(vm.categories+d.categoryLimits.keys).distinct()
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Title("Бюджет")}
        item{MonthPicker(vm,month)}
        item{MoneyField(value,{value=it},"Общий лимит на каждый месяц, ₽",zero=true)}
        item{Button(onClick={vm.setBudget(parsed!!)},enabled=!busy&&parsed!=null&&parsed>=0,modifier=Modifier.fillMaxWidth()){Text("Сохранить общий лимит")}}
        item{Text("Пустое поле или 0 убирает лимит. Возвраты уменьшают расходы, переводы между счетами не расходуют бюджет.",style=MaterialTheme.typography.bodySmall)}
        if(d.budget>0)item{Metric("Расходы за месяц",BudgetEngine.netExpenses(d,month),Modifier.fillMaxWidth())}
        item{HorizontalDivider()}
        item{Text("Лимиты по категориям",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){categories.forEach{c->FilterChip(category==c,{category=c},label={Text(c)})}}}
        item{OutlinedTextField(category,{category=it.take(60)},label={Text("Категория")},singleLine=true,modifier=Modifier.fillMaxWidth())}
        item{MoneyField(limit,{limit=it},"Лимит категории, ₽",zero=true)}
        item{Button(onClick={vm.setCategoryLimit(category,parsedLimit!!)},enabled=!busy&&category.isNotBlank()&&parsedLimit!=null&&parsedLimit>=0,modifier=Modifier.fillMaxWidth()){Text("Сохранить лимит категории")}}
        items(d.categoryLimits.toList(),key={it.first}){(c,l)->
            val spent=BudgetEngine.netExpenses(d,month,c)
            Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(c,fontWeight=FontWeight.SemiBold);Text(rub(spent)+" из "+rub(l))
                LinearProgressIndicator(progress={(spent.toFloat()/l).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                if(spent>=l*8/10)Text(if(spent>l)"Лимит превышен"else"Лимит почти исчерпан",color=MaterialTheme.colorScheme.error)
            }}
        }
    }
}
@Composable private fun SavingsScreen(vm:AppViewModel){
    val d by vm.data.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    var value by rememberSaveable{mutableStateOf("")};var withdraw by remember{mutableStateOf(false)}
    var goalDialog by remember{mutableStateOf(false)};var delete by remember{mutableStateOf<Goal?>(null)}
    val amount=Money.parse(value)
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Title("Накопления и цели")}
        item{Metric("Свободные накопления",d.savings,Modifier.fillMaxWidth())}
        item{Text("Это деньги, отложенные из остатков счетов. Пополнение не создаёт доход, снятие не создаёт расход.",style=MaterialTheme.typography.bodySmall)}
        item{MoneyField(value,{value=it},"Сумма, ₽")}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={vm.addToSavings(amount!!);value=""},enabled=!busy&&amount!=null&&amount>0,modifier=Modifier.weight(1f)){Text("Отложить")};OutlinedButton(onClick={withdraw=true},enabled=!busy&&amount!=null&&amount in 1..d.savings,modifier=Modifier.weight(1f)){Text("Взять")}}}
        item{Metric("Всего с целями",BudgetEngine.savingsTotal(d),Modifier.fillMaxWidth())}
        item{HorizontalDivider()}
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("Совместные цели",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={goalDialog=true}){Text("+ Цель")}}}
        if(d.goals.isEmpty())item{Empty("Добавь цель: отпуск, машина или подушка безопасности.")}
        items(d.goals,key={it.id}){g->GoalCard(vm,g,d.savings){delete=g}}
    }
    if(withdraw)AlertDialog(onDismissRequest={withdraw=false},title={Text("Взять из накоплений?")},text={Text(rub(amount?:0)+" снова войдёт в доступные деньги.")},confirmButton={TextButton(onClick={vm.withdrawSavings(amount?:0);value="";withdraw=false}){Text("Подтвердить")}},dismissButton={TextButton(onClick={withdraw=false}){Text("Отмена")}})
    if(goalDialog){
        var name by rememberSaveable{mutableStateOf("")};var target by rememberSaveable{mutableStateOf("")};val n=Money.parse(target)
        AlertDialog(onDismissRequest={goalDialog=false},title={Text("Новая цель")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(name,{name=it.take(60)},label={Text("Название")},singleLine=true);MoneyField(target,{target=it},"Сумма цели, ₽")}},
            confirmButton={TextButton(onClick={vm.createGoal(name,n!!){goalDialog=false}},enabled=!busy&&name.isNotBlank()&&n!=null&&n>0){Text("Создать цель")}},dismissButton={TextButton(onClick={goalDialog=false}){Text("Отмена")}})
    }
    delete?.let{g->AlertDialog(onDismissRequest={delete=null},title={Text("Удалить цель?")},text={Text(rub(g.currentAmount)+" вернётся в свободные накопления.")},confirmButton={TextButton(onClick={vm.deleteGoal(g.id);delete=null}){Text("Удалить")}},dismissButton={TextButton(onClick={delete=null}){Text("Отмена")}})}
}
@Composable private fun GoalCard(vm:AppViewModel,g:Goal,savings:Long,onDelete:()->Unit){
    val busy by vm.busy.collectAsStateWithLifecycle()
    var value by rememberSaveable(g.id){mutableStateOf("")};var returnMoney by remember{mutableStateOf(false)}
    val amount=Money.parse(value);val transfer=if(amount==null)0 else minOf(amount,savings,g.targetAmount-g.currentAmount)
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text(g.name,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));TextButton(onClick=onDelete){Text("Удалить")}}
        Text(rub(g.currentAmount)+" из "+rub(g.targetAmount))
        LinearProgressIndicator(progress={(g.currentAmount.toFloat()/g.targetAmount).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
        if(g.currentAmount==g.targetAmount)Text("Цель достигнута",color=MaterialTheme.colorScheme.primary)
        MoneyField(value,{value=it},"Из свободных накоплений, ₽")
        if(transfer>0)Text("Внесём "+rub(transfer),style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button(onClick={vm.fundGoal(g.id,amount!!);value=""},enabled=!busy&&transfer>0){Text("Внести")}
            TextButton(onClick={returnMoney=true},enabled=!busy&&amount!=null&&amount in 1..g.currentAmount){Text("Вернуть в накопления")}
        }
    }}
    if(returnMoney)AlertDialog(onDismissRequest={returnMoney=false},title={Text("Вернуть из цели?")},text={Text(rub(amount?:0)+" вернётся в свободные накопления.")},confirmButton={TextButton(onClick={vm.withdrawGoal(g.id,amount?:0);value="";returnMoney=false}){Text("Вернуть")}},dismissButton={TextButton(onClick={returnMoney=false}){Text("Отмена")}})
}
@Composable private fun AccountsScreen(vm:AppViewModel,onBack:()->Unit){
    val d by vm.data.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    var editing by remember{mutableStateOf<MoneyAccount?>(null)};var create by remember{mutableStateOf(false)};var archive by remember{mutableStateOf<MoneyAccount?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Title("Счета")};TextButton(onClick=onBack){Text("Назад")}}}
        item{Text("Начальный остаток — деньги на счёте до первой записанной операции. Архивный счёт остаётся в истории и общем балансе.",style=MaterialTheme.typography.bodySmall)}
        item{Button(onClick={create=true},modifier=Modifier.fillMaxWidth()){Text("+ Добавить счёт")}}
        items(d.accounts,key={it.id}){a->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(a.name+(if(a.archived)" · Архив"else""),fontWeight=FontWeight.SemiBold)
            Text(rub(BudgetEngine.balance(d,a.id)),style=MaterialTheme.typography.titleLarge)
            Text("Начальный остаток: "+rub(a.openingBalance),style=MaterialTheme.typography.bodySmall)
            Row{TextButton(onClick={editing=a}){Text("Изменить")};TextButton(onClick={if(a.archived)vm.archiveAccount(a.id,false)else archive=a},enabled=!busy){Text(if(a.archived)"Вернуть"else"В архив")}}
        }}}
    }
    if(create||editing!=null){
        val current=editing
        var name by rememberSaveable(current?.id){mutableStateOf(current?.name.orEmpty())}
        var balance by rememberSaveable(current?.id){mutableStateOf(Money.edit(current?.openingBalance?:0))}
        val n=Money.parse(balance)
        AlertDialog(onDismissRequest={create=false;editing=null},title={Text(if(current==null)"Новый счёт"else"Изменить счёт")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(name,{name=it.take(60)},label={Text("Название")},singleLine=true);MoneyField(balance,{balance=it},"Начальный остаток, ₽",zero=true,negative=true)}},
            confirmButton={TextButton(onClick={vm.saveAccount(MoneyAccount(current?.id?:UUID.randomUUID().toString(),name.trim(),n!!,current?.archived?:false)){create=false;editing=null}},enabled=!busy&&name.isNotBlank()&&n!=null){Text("Сохранить счёт")}},dismissButton={TextButton(onClick={create=false;editing=null}){Text("Отмена")}})
    }
    archive?.let{a->AlertDialog(onDismissRequest={archive=null},title={Text("Убрать счёт в архив?")},text={Text("«"+a.name+"» сохранит остаток и старые операции. Новые операции с ним будут недоступны.")},confirmButton={TextButton(onClick={vm.archiveAccount(a.id,true);archive=null}){Text("В архив")}},dismissButton={TextButton(onClick={archive=null}){Text("Отмена")}})}
}
@Composable private fun BackupPanel(vm:AppViewModel,recovery:Boolean=false){
    val busy by vm.busy.collectAsStateWithLifecycle();val session by vm.session.collectAsStateWithLifecycle();val context=LocalContext.current
    var mode by remember{mutableStateOf("")};var uri by remember{mutableStateOf<Uri?>(null)};var password by remember{mutableStateOf<CharArray?>(null)}
    DisposableEffect(Unit){onDispose{password?.fill('\u0000')}}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){u->
        val p=password;password=null
        if(u!=null&&p!=null)vm.exportBackup(u,p)else p?.fill('\u0000')
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){uri=u;mode="restore"}}
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        if(!recovery)OutlinedButton(onClick={mode="save"},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Создать резервную копию с паролем")}
        OutlinedButton(onClick={open.launch(arrayOf("*/*"))},enabled=!busy&&session==null,modifier=Modifier.fillMaxWidth()){Text("Восстановить резервную копию")}
        Text(if(session!=null)"Для восстановления отключи общий бюджет. Создавать копии можно в любое время."else"Копия включает счета, операции, накопления, цели и имена. Облачные ключи в неё не входят. Без пароля восстановить копию нельзя.",style=MaterialTheme.typography.bodySmall)
    }
    if(mode.isNotBlank()){
        var first by remember{mutableStateOf("")};var second by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={mode="";uri=null},title={Text(if(mode=="save")"Пароль резервной копии"else"Восстановить бюджет?")},
            text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(mode=="restore")Text("Текущий локальный бюджет будет заменён содержимым файла.")
                OutlinedTextField(first,{first=it.take(128)},label={Text("Пароль · минимум 10 символов")},visualTransformation=PasswordVisualTransformation(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
                if(mode=="save")OutlinedTextField(second,{second=it.take(128)},label={Text("Повтори пароль")},visualTransformation=PasswordVisualTransformation(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            }},
            confirmButton={TextButton(onClick={
                if(mode=="save"){password=first.toCharArray();mode="";save.launch("DuoBudget-"+LocalDate.now()+".duoback")}
                else{val u=uri;mode="";uri=null;if(u!=null)vm.restoreBackup(u,first.toCharArray())}
                first="";second=""
            },enabled=first.length>=10&&(mode!="save"||first==second)){Text(if(mode=="save")"Сохранить копию"else"Восстановить")}},
            dismissButton={TextButton(onClick={mode="";uri=null}){Text("Отмена")}})
    }
}
@Composable private fun SettingsScreen(vm:AppViewModel,onAccounts:()->Unit){
    val d by vm.data.collectAsStateWithLifecycle();val dark by vm.darkTheme.collectAsStateWithLifecycle();val session by vm.session.collectAsStateWithLifecycle()
    val sync by vm.syncState.collectAsStateWithLifecycle();val syncMessage by vm.syncMessage.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    val changes by vm.changes.collectAsStateWithLifecycle();val members by vm.members.collectAsStateWithLifecycle();val pending by vm.pending.collectAsStateWithLifecycle();val month by vm.month.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var myName by rememberSaveable(d.myName,d.partnerName){mutableStateOf(vm.payerName(vm.myPayer))}
    var partnerName by rememberSaveable(d.myName,d.partnerName){mutableStateOf(vm.payerName(if(vm.myPayer==Payer.ME)Payer.PARTNER else Payer.ME))}
    var code by rememberSaveable{mutableStateOf("")};var connectConfirmation by remember{mutableStateOf(false)}
    var joining by remember{mutableStateOf(false)};var disconnect by remember{mutableStateOf(false)};var reset by remember{mutableStateOf(false)};var privacy by remember{mutableStateOf(false)}
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->if(uri!=null)vm.exportCsv(uri,month)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Title("Ещё")
        Card(Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text("Тёмная тема",fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Switch(dark,vm::setDark,enabled=!busy)}}
        Text("Профили",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        OutlinedTextField(myName,{myName=it.take(60)},label={Text("Моё имя")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(partnerName,{partnerName=it.take(60)},label={Text("Имя партнёра")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Button(onClick={vm.saveNames(myName,partnerName)},enabled=!busy&&myName.isNotBlank()&&partnerName.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Сохранить имена")}
        OutlinedButton(onClick=onAccounts,modifier=Modifier.fillMaxWidth()){Text("Счета и начальные остатки")}
        HorizontalDivider()
        Text("Два телефона",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(syncLabel(sync),fontWeight=FontWeight.SemiBold);Text(syncMessage);if(session!=null){Text("Подключено телефонов: "+members);if(pending>0)Text("Ожидает отправки: "+pending)}}}
        if(session==null){
            Text("После подключения данные семьи отправляются на сервер. Каждый меняет только свои операции. Без интернета изменения сохраняются на телефоне.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={joining=false;connectConfirmation=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Создать общий бюджет")}
            OutlinedTextField(code,{code=it.uppercase(Locale.ROOT).filter{ch->ch in 'A'..'Z'||ch in '2'..'9'}.take(16)},label={Text("Код приглашения · 16 символов")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedButton(onClick={joining=true;connectConfirmation=true},enabled=!busy&&code.length==16,modifier=Modifier.fillMaxWidth()){Text("Подключиться по коду")}
        }else{
            if(session?.role==Payer.ME&&members==1){
                if(session!!.inviteCode.isNotBlank())Text("Приглашение: "+session!!.inviteCode,fontWeight=FontWeight.SemiBold)
                Text("Одноразовый код действует 24 часа.",style=MaterialTheme.typography.bodySmall)
                if(session!!.inviteCode.isNotBlank())OutlinedButton(onClick={
                    val send=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Подключись к нашему бюджету DuoBudget. Код: "+session!!.inviteCode)
                    context.startActivity(Intent.createChooser(send,"Передать приглашение"))
                },modifier=Modifier.fillMaxWidth()){Text("Передать приглашение")}
                OutlinedButton(onClick=vm::refreshInvite,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Обновить приглашение")}
            }
            Button(onClick=vm::retrySync,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Синхронизировать сейчас")}
            OutlinedButton(onClick={disconnect=true},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(session?.role==Payer.ME)"Удалить общее пространство"else"Отключить этот телефон")}
        }
        HorizontalDivider()
        Text("Данные",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        BackupPanel(vm)
        OutlinedButton(onClick={csv.launch("DuoBudget-"+month+".csv")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Выгрузить операции за "+month)}
        TextButton(onClick={privacy=true}){Text("Конфиденциальность")}
        if(session==null)TextButton(onClick={reset=true},enabled=!busy){Text("Удалить все локальные данные")}
        HorizontalDivider()
        Text("Последние изменения",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        if(changes.isEmpty())Empty("Изменений пока нет")
        changes.take(20).forEach{c->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(c.action,fontWeight=FontWeight.SemiBold);if(c.description.isNotBlank())Text(c.description);Text(c.changedAt.format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")),style=MaterialTheme.typography.bodySmall)}}}
        Text("DuoBudget "+BuildConfig.VERSION_NAME+" · Семейный бюджет",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(connectConfirmation)AlertDialog(onDismissRequest={connectConfirmation=false},title={Text(if(joining)"Подключить телефон?"else"Создать общий бюджет?")},text={Text("Счета, операции, накопления, цели и имена будут переданы сервису синхронизации. При подключении по коду локальные операции добавятся к бюджету партнёра. Вы можете удалить данные с сервера в этом разделе.")},confirmButton={TextButton(onClick={connectConfirmation=false;if(joining)vm.joinFamily(code)else vm.createFamily()}){Text("Подключить")}},dismissButton={TextButton(onClick={connectConfirmation=false}){Text("Отмена")}})
    if(disconnect)AlertDialog(onDismissRequest={disconnect=false},title={Text(if(session?.role==Payer.ME)"Удалить общий бюджет с сервера?"else"Отключить телефон?")},text={Text(if(session?.role==Payer.ME)"Оба телефона потеряют доступ к этому пространству. Локальные копии останутся на устройствах."else"Твой телефон перестанет синхронизироваться. Данные, уже переданные партнёру, останутся в общем бюджете.")},confirmButton={TextButton(onClick={disconnect=false;vm.disconnect()}){Text("Подтвердить")}},dismissButton={TextButton(onClick={disconnect=false}){Text("Отмена")}})
    if(reset)AlertDialog(onDismissRequest={reset=false},title={Text("Удалить локальный бюджет?")},text={Text("Будут удалены все операции, счета, цели и накопления на этом телефоне. Сначала сохрани резервную копию.")},confirmButton={TextButton(onClick={reset=false;vm.reset()}){Text("Удалить данные")}},dismissButton={TextButton(onClick={reset=false}){Text("Отмена")}})
    if(privacy)AlertDialog(onDismissRequest={privacy=false},title={Text("Конфиденциальность")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Приложение не подключается к банкам, не читает контакты и не содержит рекламной аналитики.")
        Text("На телефоне данные шифруются AES-256-GCM, ключ хранится в Android Keystore. Вход защищён блокировкой устройства, если она настроена.")
        Text("Общий бюджет включается отдельно. Данные передаются по HTTPS и сохраняются на сервере. Это не сквозное шифрование: администратор сервиса технически может прочитать серверные данные.")
        Text("Снимки экранов и системные резервные копии отключены. Для переноса данных используйте резервную копию с паролем.")
        Text("Удалить серверные данные может первый участник в разделе «Два телефона». Копии на устройствах и резервные файлы удаляются отдельно.")
    }},confirmButton={TextButton(onClick={privacy=false}){Text("Понятно")}})
}

