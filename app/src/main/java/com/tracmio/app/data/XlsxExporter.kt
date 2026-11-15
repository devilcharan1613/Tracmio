package com.tracmio.app.data

import com.tracmio.app.data.local.AccountEntity
import com.tracmio.app.data.local.TransactionEntity
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExporter {
    fun createWorkbookBytes(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>
    ): ByteArray {
        val accountMap = accounts.associate { it.id to it.name }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            fun writeEntry(name: String, xml: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(xml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            writeEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            )

            writeEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            writeEntry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Transactions" sheetId="1" r:id="rId1"/>
    <sheet name="Accounts" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""
            )

            writeEntry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
</Relationships>"""
            )

            val txRows = StringBuilder()
            txRows.append(
                buildHeaderRow(
                    1,
                    listOf("Date", "Account", "Type", "Category", "Subcategory", "Amount", "Payment Method", "UPI App", "Note", "Hashtags")
                )
            )
            transactions.forEachIndexed { idx, tx ->
                val r = idx + 2
                val dateStr = dateFmt.format(Date(tx.timestamp))
                val accName = accountMap[tx.accountId] ?: "Account #${tx.accountId}"
                val safeAmt = if (tx.amount.isFinite()) tx.amount else 0.0
                txRows.append(
                    """<row r="$r">""" +
                        inlineCell(dateStr) +
                        inlineCell(accName) +
                        inlineCell(tx.type) +
                        inlineCell(tx.category) +
                        inlineCell(tx.subcategory) +
                        """<c><v>${"%.2f".format(Locale.US, safeAmt)}</v></c>""" +
                        inlineCell(tx.paymentMethod) +
                        inlineCell(tx.upiApp) +
                        inlineCell(tx.note) +
                        inlineCell(tx.hashtags) +
                        """</row>"""
                )
            }

            writeEntry(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>$txRows</sheetData>
</worksheet>"""
            )

            val accRows = StringBuilder()
            accRows.append(buildHeaderRow(1, listOf("Name", "Type", "Opening Balance", "Live Balance", "Credit Limit")))
            accounts.forEachIndexed { idx, acc ->
                val r = idx + 2
                val liveBal = FinancialCalculator.balance(acc, transactions)
                accRows.append(
                    """<row r="$r">""" +
                        inlineCell(acc.name) +
                        inlineCell(acc.accountType) +
                        """<c><v>${"%.2f".format(Locale.US, acc.balance)}</v></c>""" +
                        """<c><v>${"%.2f".format(Locale.US, liveBal)}</v></c>""" +
                        """<c><v>${"%.2f".format(Locale.US, acc.creditLimit)}</v></c>""" +
                        """</row>"""
                )
            }

            writeEntry(
                "xl/worksheets/sheet2.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>$accRows</sheetData>
</worksheet>"""
            )
        }
        return bos.toByteArray()
    }

    private fun buildHeaderRow(rowIndex: Int, headers: List<String>): String {
        return """<row r="$rowIndex">""" + headers.joinToString("") { """<c t="inlineStr"><is><t>${escapeXml(it)}</t></is></c>""" } + """</row>"""
    }

    private fun inlineCell(raw: String): String {
        val guarded = CsvParser.sanitizeFormulaCell(raw)
        return """<c t="inlineStr"><is><t>${escapeXml(guarded)}</t></is></c>"""
    }

    private fun escapeXml(s: String): String {
        val cleaned = s.filter { ch ->
            val code = ch.code
            code == 0x09 || code == 0x0A || code == 0x0D || code >= 0x20
        }
        return cleaned
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
