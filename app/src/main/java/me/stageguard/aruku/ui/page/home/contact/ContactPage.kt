package me.stageguard.aruku.ui.page.home.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.pager.*
import kotlinx.coroutines.launch
import me.stageguard.aruku.R
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.ui.LocalBot
import me.stageguard.aruku.ui.common.EmptyListWhitePage
import me.stageguard.aruku.ui.common.pagerTabIndicatorOffsetMD3
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC
import org.koin.androidx.compose.koinViewModel

/**
 * Created by LoliBall on 2022/12/31 11:57.
 * https://github.com/WhichWho
 */
@OptIn(ExperimentalPagerApi::class)
@Composable
fun HomeContactPage(padding: PaddingValues) {
    val bot = LocalBot.current
    val viewModel: ContactViewModel = koinViewModel()

    val friends = viewModel.friends.value?.collectAsLazyPagingItems()
    val groups = viewModel.groups.value?.collectAsLazyPagingItems()

    LaunchedEffect(bot) { if (bot != null) viewModel.initContacts(bot) }

    val pagerState = rememberPagerState()
    val contactTabs = listOf(
        ContactTab(R.string.contact_tab_friend) {
            EmptyListWhitePage(data = friends) { friends?.refresh() }
            ContactTabContent(friends?.itemSnapshotList?.toList()?.filterNotNull() ?: listOf()) {

            }
        },
        ContactTab(R.string.contact_tab_group) {
            EmptyListWhitePage(data = groups) { groups?.refresh() }
            ContactTabContent(groups?.itemSnapshotList?.toList()?.filterNotNull() ?: listOf()) {

            }
        }
    )

    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        ContactTabs(contactTabs, pagerState = pagerState)
        HorizontalPager(state = pagerState, count = contactTabs.count()) { index ->
            contactTabs[index].content()
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ContactTabs(tabs: List<ContactTab>, pagerState: PagerState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    TabRow(
        selectedTabIndex = pagerState.currentPage,
        indicator = { tabPos ->
            TabRowDefaults.Indicator(Modifier.pagerTabIndicatorOffsetMD3(pagerState, tabPos))
        },
        modifier = modifier
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = {
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                },
                text = { Text(tab.title.stringResC) }
            )
        }
    }
}

@Composable
fun ContactTabContent(
    data: List<SimpleContactData>,
    modifier: Modifier = Modifier,
    onContactClick: (ArukuContact) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(5.dp)
    ) {
        items(data, key = { it.contact }) {
            ContactItem(it, modifier = Modifier.clickable { onContactClick(it.contact) })
        }
    }
}

@Composable
fun ContactItem(data: SimpleContactData, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier, elevation = CardDefaults.elevatedCardElevation(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.padding(12.dp).size(40.dp),
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                AsyncImage(
                    ImageRequest.Builder(LocalContext.current)
                        .data(data.avatarData)
                        .crossfade(true)
                        .build(),
                    "message avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.padding(start = 2.dp).align(Alignment.CenterVertically)) {
                Text(
                    text = data.name,
                    modifier = Modifier,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
                Text(
                    text = data.contact.subject.toString(),
                    modifier = Modifier,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.SemiBold
                    ),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Preview
@Composable
fun ContactTabPreview() {
    ArukuTheme {
        val pagerState = rememberPagerState()
        val tabs = listOf(
            ContactTab(R.string.contact_tab_friend) { Text("friends") },
            ContactTab(R.string.contact_tab_group) { Text("groups") },
            ContactTab(R.string.app_name) { Text("application") }
        )
        Column {
            ContactTabs(tabs, pagerState)
            HorizontalPager(state = pagerState, count = tabs.count()) { index ->
                tabs[index].content()
            }
        }
    }
}

@Preview
@Composable
fun ContactTabContentPreview() {
    ArukuTheme {
        ContactTabContent(
            data = listOf(
                SimpleContactData(ArukuContact(ArukuContactType.GROUP, 1234556L), "group 1", R.mipmap.ic_launcher),
                SimpleContactData(ArukuContact(ArukuContactType.GROUP, 114514L), "group 2", R.mipmap.load_loading),
                SimpleContactData(ArukuContact(ArukuContactType.FRIEND, 1919810L), "friend 1", R.mipmap.load_empty)
            )
        ) { }
    }
}