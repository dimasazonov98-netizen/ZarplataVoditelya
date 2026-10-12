package com.duobudget.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.duobudget.app.model.*
import com.duobudget.app.viewmodel.AppViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

private val monthFormat=DateTimeFormatter.ofPattern("MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
private fun recurringMoney(value:Long)=Money.edit(value)+" ₽"

@Composable
fun RecurringExpensesScreen(vm:AppViewModel,onAdd:()->Unit,onEdit:(String)->Unit){
    val rules by vm.recurringExpenses.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val current=YearMonth.now()
    var removing by remember{mutableStateOf<RecurringExpense?>(null)}
    val total=rules.filter{it.enabled&&YearMonth.parse(it.startMonth)<=current&&data.accounts.any{a->a.id==it.accountId&&!a.archived}}.sumOf{it.amount}
    LazyColumn(Modifier.fillMaxSize().testTag("screen-list"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Обязательные расходы",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
        item{Text("Кредит, ЖКХ, аренда — один раз в месяц. Расходы сразу входят в операции и уменьшают доступный остаток. Пропущенные месяцы добавятся при открытии приложения.")}
        item{Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.66f))){Column(Modifier.padding(16.dp)){
            Text("Ежемесячно по активным правилам",style=MaterialTheme.typography.bodyMedium)
            Text(recurringMoney(total),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        }}}
        item{Button(onClick=onAdd,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("fixed-add")){Text("+ Обязательный расход")}}
        if(session!=null)item{Text("Правила создают расходы на этом телефоне. Записанные операции синхронизируются с партнёром. Не создавайте одно и то же правило на обоих телефонах.",style=MaterialTheme.typography.bodySmall)}
        if(rules.isEmpty())item{Text("Добавь первый платёж — например, ежемесячный кредит.",Modifier.padding(vertical=16.dp))}
        items(rules.sortedBy{it.name},key={it.id}){r->
            val account=data.accounts.firstOrNull{it.id==r.accountId}
            Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.66f))){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(r.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text(recurringMoney(r.amount)+" / месяц",style=MaterialTheme.typography.titleMedium)
                Text(r.category+" · "+(account?.name?:"Счёт не найден")+" · "+vm.payerName(r.payer))
                Text("С "+YearMonth.parse(r.startMonth).format(monthFormat),style=MaterialTheme.typography.bodySmall)
                if(account==null||account.archived)Text("Счёт недоступен. Измени правило, чтобы расходы продолжили добавляться.",color=MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text(if(r.enabled)"Учитывать каждый месяц"else"Приостановлено",Modifier.weight(1f).padding(top=12.dp))
                    Switch(checked=r.enabled,onCheckedChange={vm.setRecurringEnabled(r,it)},enabled=!busy&&(r.enabled||account?.archived==false),modifier=Modifier.testTag("fixed-toggle-"+r.id))
                }
                Row{
                    TextButton(onClick={onEdit(r.id)},enabled=!busy,modifier=Modifier.testTag("fixed-edit-"+r.id)){Text("Изменить")}
                    TextButton(onClick={removing=r},enabled=!busy,modifier=Modifier.testTag("fixed-delete-"+r.id)){Text("Удалить правило")}
                }
            }}
        }
    }
    removing?.let{r->AppDialog(onDismissRequest={removing=null},title={Text("Удалить обязательный расход?")},text={Text("«${r.name}» больше не будет добавляться. Уже записанные операции сохранятся.")},
        confirmButton={TextButton(onClick={vm.removeRecurring(r.id);removing=null},modifier=Modifier.testTag("fixed-delete-confirm")){Text("Удалить правило")}},dismissButton={TextButton(onClick={removing=null}){Text("Отмена")}})}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringExpenseForm(vm:AppViewModel,id:String?,onDone:()->Unit){
    val data by vm.data.collectAsStateWithLifecycle()
    val rules by vm.recurringExpenses.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val old=rules.firstOrNull{it.id==id}
    val ruleId=rememberSaveable(id){old?.id?:UUID.randomUUID().toString()}
    var name by rememberSaveable(id){mutableStateOf(old?.name.orEmpty())}
    var amount by rememberSaveable(id){mutableStateOf(old?.amount?.let(Money::edit).orEmpty())}
    var category by rememberSaveable(id){mutableStateOf(old?.category?:"Кредит")}
    var account by rememberSaveable(id){mutableStateOf(old?.accountId?:data.accounts.first{!it.archived}.id)}
    var payer by rememberSaveable(id){mutableStateOf(old?.payer?:vm.myPayer)}
    var firstMonth by rememberSaveable(id){mutableStateOf(YearMonth.parse(old?.startMonth?:YearMonth.now().toString()).format(monthFormat))}
    var enabled by rememberSaveable(id){mutableStateOf(old?.enabled?:true)}
    val parsed=Money.parse(amount)
    val start=runCatching{YearMonth.parse(firstMonth,monthFormat)}.getOrNull()?.takeIf{it.year>=2000}
    val validAccount=data.accounts.any{it.id==account&&(!enabled||!it.archived)}
    Column(Modifier.fillMaxSize().imePadding()){
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(if(old==null)"Новый обязательный расход"else"Изменить обязательный расход",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            if(old==null)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("Кредит","ЖКХ","Аренда").forEach{preset->SuggestionChip(onClick={name=preset;category=if(preset=="Кредит")"Кредит"else"Жильё"},label={Text(preset)})}
            }
            OutlinedTextField(name,{name=it.take(60)},label={Text("Название платежа")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("fixed-name"))
            OutlinedTextField(amount,{amount=it.take(64)},label={Text("Сумма каждый месяц, ₽")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=amount.isNotBlank()&&(parsed==null||parsed<=0),modifier=Modifier.fillMaxWidth().testTag("fixed-amount"))
            OutlinedTextField(firstMonth,{firstMonth=it.take(7)},label={Text("Первый месяц · ММ.ГГГГ")},singleLine=true,isError=start==null,modifier=Modifier.fillMaxWidth().testTag("fixed-month"))
            Text("Расход учитывается 1-го числа. Если месяц уже наступил, запись появится при сохранении. Изменение суммы обновит текущий месяц и следующие; прошлые записи останутся прежними.",style=MaterialTheme.typography.bodySmall)
            Text("Категория",fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){vm.categories.forEach{c->FilterChip(selected=category==c,onClick={category=c},label={Text(c)})}}
            Text("Счёт",fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){data.accounts.filter{!it.archived||it.id==account}.forEach{a->FilterChip(selected=account==a.id,onClick={account=a.id},label={Text(a.name+if(a.archived)" · Архив"else"")})}}
            Text("Кто оплачивает",fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){Payer.entries.forEach{p->FilterChip(selected=payer==p,onClick={payer=p},label={Text(vm.payerName(p))})}}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Учитывать каждый месяц",Modifier.weight(1f).padding(top=12.dp));Switch(checked=enabled,onCheckedChange={enabled=it})}
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick={vm.saveRecurring(RecurringExpense(ruleId,name.trim(),parsed!!,category,account,payer,start!!.toString(),enabled,old?.resumeMonth.orEmpty()),onDone)},enabled=!busy&&name.isNotBlank()&&parsed!=null&&parsed>0&&start!=null&&validAccount,modifier=Modifier.fillMaxWidth().testTag("fixed-save")){Text("Сохранить правило")}
            OutlinedButton(onClick=onDone,modifier=Modifier.fillMaxWidth()){Text("Отмена")}
        }
    }
}
