package com.muort.upworker.feature.d1


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.navigation.fragment.findNavController
import com.muort.upworker.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.D1Database
import com.muort.upworker.core.model.D1Table
import com.muort.upworker.core.model.D1QueryResult
import com.muort.upworker.databinding.FragmentD1ManagerBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class D1ManagerFragment : Fragment() {

    private var _binding: FragmentD1ManagerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: D1ViewModel by viewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private var currentAccount: Account? = null
    private var currentDatabase: D1Database? = null
    private var currentTable: D1Table? = null

    private var isUserSqlExecution = false
    private var isTableDataLoad = false
    private var isDataViewMode = false // 是否处于数据查看模式

    private lateinit var databaseAdapter: DatabaseAdapter
    private lateinit var tableAdapter: TableAdapter
    // 弹窗输入SQL并执行
    private fun showExecuteSqlDialog(table: D1Table) {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_d1_execute_sql, null)
        val editTextSql = dialogView.findViewById<android.widget.EditText>(R.id.editTextSql)
        val db = currentDatabase
        val account = currentAccount
        MaterialAlertDialogBuilder(context)
            .setTitle("在表 ${table.name} 执行 SQL")
            .setView(dialogView)
            .setPositiveButton("执行") { _, _ ->
                val sql = editTextSql.text.toString()
                if (account == null || db == null) {
                    Snackbar.make(requireView(), "请先选择数据库", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (sql.isBlank()) {
                    Snackbar.make(requireView(), "请输入SQL语句", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                isUserSqlExecution = true
                viewModel.executeQuery(account, db.uuid, sql)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 全局 SQL 执行弹窗
    private fun showGlobalExecuteSqlDialog() {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_d1_execute_sql, null)
        val editTextSql = dialogView.findViewById<android.widget.EditText>(R.id.editTextSql)
        val db = currentDatabase
        val account = currentAccount
        val dbName = db?.name
        if (db == null || account == null) {
            MaterialAlertDialogBuilder(context)
                .setTitle("未选择数据库")
                .setMessage("请先在左侧选择一个数据库，再执行 SQL。")
                .setPositiveButton("确定", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("在数据库 $dbName 执行 SQL")
            .setView(dialogView)
            .setPositiveButton("执行") { _, _ ->
                val sql = editTextSql.text.toString()
                if (sql.isBlank()) {
                    Snackbar.make(requireView(), "请输入SQL语句", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                isUserSqlExecution = true
                viewModel.executeQuery(account, db.uuid, sql)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentD1ManagerBinding.inflate(inflater, container, false)
        return binding.root
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupAdapters() {
        databaseAdapter = DatabaseAdapter(
            onDatabaseClick = { db ->
                currentDatabase = db
                // 清空之前的表列表
                tableAdapter.submitList(emptyList())
                binding.tableEmptyText.visibility = View.GONE
                // 只有点击数据库时才加载数据表
                currentAccount?.let { viewModel.loadTables(it, db.uuid) }
                switchToTableListMode()
            },
            onDeleteClick = { db ->
                showDeleteDatabaseDialog(db)
            }
        )
        tableAdapter = TableAdapter(
            onTableClick = { table ->
                currentTable = table
                loadTableData(table)
            },
            onDeleteClick = { table ->
                showDeleteTableDialog(table)
            },
            onExecuteSqlClick = { table ->
                showExecuteSqlDialog(table)
            }
        )
        binding.databaseRecyclerView.adapter = databaseAdapter
        binding.tableRecyclerView.adapter = tableAdapter
    }

    private fun setupClickListeners() {
        binding.fabAddDatabase.setOnClickListener {
            showAddDatabaseDialog()
        }
        binding.fabAddTable.setOnClickListener {
            showAddTableDialog()
        }
        binding.fabExecuteSql.setOnClickListener {
            showGlobalExecuteSqlDialog()
        }
        binding.btnImport.setOnClickListener {
            Snackbar.make(binding.root, "暂未实现导入功能", Snackbar.LENGTH_SHORT).show()
        }
        binding.btnExport.setOnClickListener {
            Snackbar.make(binding.root, "暂未实现导出功能", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            accountViewModel.defaultAccount.collectLatest { account ->
                if (account != null) {
                    currentAccount = account
                    viewModel.loadDatabases(account)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.databases.collectLatest { state ->
                if (state is com.muort.upworker.core.model.UiState.Success) {
                    val dbList = state.data
                    databaseAdapter.submitList(dbList)
                    binding.databaseEmptyText.visibility = if (dbList.isEmpty()) View.VISIBLE else View.GONE
                    binding.databaseProgressBar.visibility = View.GONE
                    // 不再自动选中数据库，也不自动加载表
                    tableAdapter.submitList(emptyList())
                    binding.tableTitleText.text = "数据表"
                } else if (state is com.muort.upworker.core.model.UiState.Loading) {
                    binding.databaseProgressBar.visibility = View.VISIBLE
                } else if (state is com.muort.upworker.core.model.UiState.Error) {
                    binding.databaseProgressBar.visibility = View.GONE
                    Snackbar.make(binding.root, "数据库加载失败: ${state.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.tables.collectLatest { state ->
                if (state is com.muort.upworker.core.model.UiState.Success) {
                    val tableList = state.data.filterNot { it.name.startsWith("_cf_") || it.name.startsWith("sqlite_") }
                    tableAdapter.submitList(tableList)
                    binding.tableEmptyText.visibility = if (tableList.isEmpty()) View.VISIBLE else View.GONE
                    binding.tableProgressBar.visibility = View.GONE
                    // 数据表标签显示数据库名称
                    val dbName = currentDatabase?.name ?: ""
                    binding.tableTitleText.text = "数据表（$dbName）"
                } else if (state is com.muort.upworker.core.model.UiState.Loading) {
                    binding.tableProgressBar.visibility = View.VISIBLE
                } else if (state is com.muort.upworker.core.model.UiState.Error) {
                    binding.tableProgressBar.visibility = View.GONE
                    Snackbar.make(binding.root, "表加载失败: ${state.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.queryResult.collectLatest { state ->
                if (state is com.muort.upworker.core.model.UiState.Success) {
                    val result = state.data
                    if (isUserSqlExecution) {
                        setupRecyclerView(result)
                        // 更新标题显示SQL结果
                        binding.tableTitleText.text = "SQL 执行结果"
                        switchToDataViewMode()
                        // 新增：SQL 执行成功后弹窗详情
                        val rowCount = result.results?.size ?: 0
                        val columns = result.results?.firstOrNull()?.keys?.toList() ?: emptyList()
                        val msg = if (rowCount > 0) {
                            "共 $rowCount 行，字段：${columns.joinToString(", ")}"
                        } else {
                            "SQL 执行成功，无返回数据。"
                        }
                        // 优化：显示 Cloudflare D1 meta 详情
                        val meta = result.meta
                        // meta 字段为 Any?，需安全转为 Map<String, Any?>
                        val metaMap = (meta as? Map<*, *>)?.mapNotNull {
                            val k = it.key as? String
                            if (k != null) k to it.value else null
                        }?.toMap() ?: emptyMap()
                        val metaMsg = if (metaMap.isNotEmpty()) {
                            val sb = StringBuilder()
                            sb.appendLine(msg)
                            sb.appendLine()
                            sb.appendLine("--- Cloudflare D1 执行详情 ---")
                            sb.appendLine("SQL耗时: ${metaMap["sql_duration_ms"] ?: metaMap["duration"] ?: "-"} ms")
                            sb.appendLine("变更: ${metaMap["changes"] ?: 0}")
                            sb.appendLine("写入行: ${metaMap["rows_written"] ?: 0}")
                            sb.appendLine("读取行: ${metaMap["rows_read"] ?: 0}")
                            sb.appendLine("DB大小: ${metaMap["size_after"] ?: "-"}")
                            sb.toString()
                        } else msg
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("SQL 执行结果")
                            .setMessage(metaMsg)
                            .setPositiveButton("确定", null)
                            .show()
                        // 自动刷新表和数据列表
                        val account = currentAccount
                        val db = currentDatabase
                        if (account != null && db != null) {
                            viewModel.loadTables(account, db.uuid)
                            // 可选：如有表选中，刷新表数据
                            currentTable?.let { loadTableData(it) }
                        }
                        isUserSqlExecution = false
                    } else if (isTableDataLoad) {
                        // 导航到数据查看器Fragment，传递数据库和表信息
                        val bundle = Bundle().apply {
                            putString("title", "${currentTable?.name ?: "表"} 数据 (前100行)")
                            putString("databaseId", currentDatabase?.uuid)
                            putString("tableName", currentTable?.name)
                        }
                        findNavController().navigate(R.id.d1DataViewerFragment, bundle)
                        isTableDataLoad = false
                    }
                } else if (state is com.muort.upworker.core.model.UiState.Error) {
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAddDatabaseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_d1_create_database, null)
        val editName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editDatabaseName)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新建数据库")
            .setView(dialogView)
            .setPositiveButton("创建") { dialog, _ ->
                val name = editName.text?.toString()?.trim()
                if (name.isNullOrEmpty()) {
                    Snackbar.make(binding.root, "数据库名称不能为空", Snackbar.LENGTH_SHORT).show()
                } else {
                    val account = currentAccount
                    if (account == null) {
                        Snackbar.make(binding.root, "未获取到账号信息", Snackbar.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    lifecycleScope.launch {
                        val result = viewModel.createDatabase(account, name)
                        if (result) {
                            Snackbar.make(binding.root, "数据库创建成功", Snackbar.LENGTH_SHORT).show()
                            viewModel.loadDatabases(account)
                        } else {
                            Snackbar.make(binding.root, "数据库创建失败", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteDatabaseDialog(db: D1Database) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除数据库")
            .setMessage("确定要删除数据库 \"${db.name}\" 吗？")
            .setPositiveButton("删除") { dialog, _ ->
                val account = currentAccount
                if (account == null) {
                    Snackbar.make(binding.root, "未获取到账号信息", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val result = viewModel.deleteDatabase(account, db.uuid)
                    if (result) {
                        Snackbar.make(binding.root, "数据库已删除", Snackbar.LENGTH_SHORT).show()
                        viewModel.loadDatabases(account)
                    } else {
                        Snackbar.make(binding.root, "数据库删除失败", Snackbar.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddTableDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新建表")
            .setMessage("暂未实现新建表功能")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showDeleteTableDialog(table: D1Table) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除表")
            .setMessage("确定要删除表 \"${table.name}\" 吗？")
            .setPositiveButton("删除") { dialog, _ ->
                val account = currentAccount
                val db = currentDatabase
                if (account == null || db == null) {
                    Snackbar.make(requireView(), "请先选择数据库", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val sql = "DROP TABLE IF EXISTS `${table.name}`;"
                viewModel.executeQuery(account, db.uuid, sql)
                // 删除后刷新表列表
                viewModel.loadTables(account, db.uuid)
                Snackbar.make(requireView(), "表已删除", Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupAccountAndDatabase() {
        // 监听账号变化，加载数据库
        lifecycleScope.launch {
            accountViewModel.defaultAccount.collectLatest { account ->
                if (account != null) {
                    currentAccount = account
                    viewModel.loadDatabases(account)
                }
            }
        }
        // 监听数据库加载
        lifecycleScope.launch {
            viewModel.databases.collectLatest { state ->
                if (state is com.muort.upworker.core.model.UiState.Success) {
                    val dbList = state.data
                    if (dbList.isNotEmpty()) {
                        currentDatabase = dbList[0]
                        viewModel.loadTables(currentAccount!!, dbList[0].uuid)
                    }
                }
            }
        }
    }

    private fun setupTableSpinner() {
        // 监听表加载
        lifecycleScope.launch {
            viewModel.tables.collectLatest { state ->
                if (state is com.muort.upworker.core.model.UiState.Success) {
                    // 过滤掉 _cf_KV、sqlite_sequence 等系统表
                    val tableList = state.data.filterNot { it.name.startsWith("_cf_") || it.name.startsWith("sqlite_") }
                    if (tableList.isNotEmpty()) {
                        currentTable = tableList[0]
                        loadTableData(tableList[0])
                    }
                } else if (state is com.muort.upworker.core.model.UiState.Error) {
                }
            }
        }
    }

    private fun loadTableData(table: D1Table) {
        val sql = "SELECT * FROM ${table.name} LIMIT 100"
        val account = currentAccount ?: return
        val db = currentDatabase ?: return
        isTableDataLoad = true
        viewModel.executeQuery(account, db.uuid, sql)
    }

    private fun setupRecyclerView(result: D1QueryResult) {
        val columns = result.results?.firstOrNull()?.keys?.toList() ?: emptyList()
        val rows = result.results ?: emptyList()
        binding.recyclerViewData.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewData.adapter = D1DataAdapter(columns, rows)
    }


    // region Adapter
    private class DatabaseAdapter(
        private val onDatabaseClick: (D1Database) -> Unit,
        private val onDeleteClick: (D1Database) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<DatabaseAdapter.ViewHolder>() {
        private var databases = listOf<D1Database>()
        fun submitList(newList: List<D1Database>) {
            databases = newList
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_d1_database, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(databases[position])
        }
        override fun getItemCount() = databases.size
        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            fun bind(db: D1Database) {
                val nameText = itemView.findViewById<android.widget.TextView>(R.id.databaseNameText)
                val uuidText = itemView.findViewById<android.widget.TextView>(R.id.databaseUuidText)
                val deleteBtn = itemView.findViewById<android.widget.ImageButton>(R.id.deleteDatabaseBtn)
                nameText.text = db.name
                uuidText.text = "ID: ${db.uuid}"
                itemView.setOnClickListener { onDatabaseClick(db) }
                deleteBtn.setOnClickListener { onDeleteClick(db) }
            }
        }
    }

    private class TableAdapter(
        private val onTableClick: (D1Table) -> Unit,
        private val onDeleteClick: (D1Table) -> Unit,
        private val onExecuteSqlClick: (D1Table) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<TableAdapter.ViewHolder>() {
        private var tables = listOf<D1Table>()
        fun submitList(newList: List<D1Table>) {
            tables = newList
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_d1_table, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(tables[position])
        }
        override fun getItemCount() = tables.size
        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            fun bind(table: D1Table) {
                val nameText = itemView.findViewById<android.widget.TextView>(R.id.tableNameText)
                val columnsText = itemView.findViewById<android.widget.TextView>(R.id.tableColumnsText)
                val deleteBtn = itemView.findViewById<android.widget.ImageButton>(R.id.deleteTableBtn)
                nameText.text = table.name
                columnsText.text = "字段数: ${table.columns?.size ?: 0}"
                itemView.setOnClickListener { onTableClick(table) }
                deleteBtn.setOnClickListener { onDeleteClick(table) }
                itemView.setOnLongClickListener {
                    onExecuteSqlClick(table)
                    true
                }
            }
        }
    }
    // endregion

    private fun showTableDataDialog(result: D1QueryResult) {
        val context = requireContext()
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_d1_table_data, null)
        val tableLayout = dialogView.findViewById<android.widget.TableLayout>(R.id.tableLayoutData)
        val rows = result.results ?: emptyList()
        val columns = rows.flatMap { it.keys }.distinct()

        if (columns.isEmpty()) {
            // No columns, show message
            val textView = android.widget.TextView(context).apply {
                text = if (rows.isEmpty()) "表为空或无数据" else "表数据无有效列"
                setPadding(16, 16, 16, 16)
                gravity = android.view.Gravity.CENTER
            }
            tableLayout.addView(textView)
        } else {
            // Set stretch columns only if there are columns
            tableLayout.isStretchAllColumns = true
            tableLayout.isShrinkAllColumns = true

            // Add header row
            val headerRow = android.widget.TableRow(context)
            for (column in columns) {
                val textView = android.widget.TextView(context).apply {
                    text = column
                    setPadding(12, 12, 12, 12)
                    setBackgroundColor(0xFFEEEEEE.toInt())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = android.view.Gravity.CENTER
                    setTextColor(0xFF000000.toInt())
                    // Add border
                    background = createBorderDrawable(0xFFCCCCCC.toInt())
                }
                headerRow.addView(textView)
            }
            tableLayout.addView(headerRow)

            // Add data rows
            for (row in rows) {
                val dataRow = android.widget.TableRow(context)
                for (column in columns) {
                    val value = row[column]?.toString() ?: "NULL"
                    val textView = android.widget.TextView(context).apply {
                        text = value
                        setPadding(12, 12, 12, 12)
                        gravity = android.view.Gravity.START
                        setTextColor(0xFF333333.toInt())
                        // Add border
                        background = createBorderDrawable(0xFFDDDDDD.toInt())
                        minWidth = 100 // Minimum width for better alignment
                    }
                    dataRow.addView(textView)
                }
                tableLayout.addView(dataRow)
            }
        }

        val tableName = currentTable?.name ?: "表"
        MaterialAlertDialogBuilder(context)
            .setTitle("$tableName 表数据 (前100行)")
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun switchToDataViewMode() {
        isDataViewMode = true
        // 隐藏表列表相关控件
        binding.tableRecyclerView.visibility = View.GONE
        binding.tableProgressBar.visibility = View.GONE
        binding.tableEmptyText.visibility = View.GONE
        binding.fabAddTable.visibility = View.GONE
        // 显示数据表格，并让它占据剩余空间
        binding.recyclerViewData.visibility = View.VISIBLE
        val params = binding.recyclerViewData.layoutParams as LinearLayout.LayoutParams
        params.height = 0
        params.weight = 1f
        binding.recyclerViewData.layoutParams = params
    }

    private fun switchToTableListMode() {
        isDataViewMode = false
        // 显示表列表相关控件
        binding.tableRecyclerView.visibility = View.VISIBLE
        // 注意：不要在这里设置tableProgressBar的可见性，让ViewModel状态驱动
        binding.tableEmptyText.visibility = View.GONE
        binding.fabAddTable.visibility = View.GONE
        // 隐藏数据表格，或恢复到固定高度
        binding.recyclerViewData.visibility = View.GONE
        val params = binding.recyclerViewData.layoutParams as LinearLayout.LayoutParams
        params.height = 200
        params.weight = 0f
        binding.recyclerViewData.layoutParams = params
        // 恢复标题
        binding.tableTitleText.text = "数据表"
    }

    private fun createBorderDrawable(color: Int): android.graphics.drawable.Drawable {
        val shape = android.graphics.drawable.ShapeDrawable()
        shape.paint.color = color
        shape.paint.style = android.graphics.Paint.Style.STROKE
        shape.paint.strokeWidth = 1f
        return shape
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
