The issue_965_blank_rows.xlsx is used to test that rows containing no values are discarded if read with keepUndefinedRows == False.

The Excel was fabricated by hand and is used to test the library's ability to handle such cases. 

This is how the file was created:
* take a valid excel file
* rename extension from xlsx to zip
* unzip it
* add empty row definitions to `xl/worksheets/sheet1.xml` (see) below)
* zip it back
* rename extension back to xlsx


The empty row definitions added to the file are as follows:
```xml
<row r="5" spans="1:7" x14ac:dyDescent="0.25">
    <c r="A5" s="1"/>
    <c r="B5" s="1"/>
</row>
````