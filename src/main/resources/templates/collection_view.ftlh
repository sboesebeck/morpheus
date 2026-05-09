<!DOCTYPE html>
<html lang="en">

<head>
    <#include "includes/header.ftlh">
    <link href="vendor/datatables/dataTables.bootstrap4.min.css" rel="stylesheet">
</head>

<body id="page-top">

<!-- Page Wrapper -->
<div id="wrapper">

    <#include "includes/sidebar.ftlh">

    <!-- Content Wrapper -->
    <div id="content-wrapper" class="d-flex flex-column">
        <#include "includes/topbar.ftlh"/>
        <!-- Main Content -->
        <div id="content">

            <!-- Begin Page Content -->
            <div class="container-fluid">
                <div class="row">
                    <div class="col-lg-6 mb-4">
                        <div class="card bg-primary text-white shadow">
                            <div class="card-body">
                                Elements
                                <div class="text-white-50 small">12123</div>
                            </div>
                        </div>
                    </div>
                    <div class="col-lg-6 mb-4">
                        <div class="card bg-success text-white shadow">
                            <div class="card-body">
                                avg. Size
                                <div class="text-white-50 small">1232</div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="row">
                    <!-- Query builder-->
                    <input type="text" class="form-control" style="width:120px; max-width: 200px;" name="fieldname1">
                    <select class="form-control" style="width:80px; max-width: 100px;" name="operator">
                        <option value="$eq">$eq</option>
                        <option value="$ne">$ne</option>
                    </select> <input class="form-control" style="width:120px; max-width: 200px;" type="text"
                                     name="value"><br>
                </div>
                <div class="row">
                    <select class="form-control" style="width:80px; max-width: 100px;" name="bool">
                        <option value="and">and</option>
                        <option value="or">or</option>
                    </select>
                    <input type="text" class="form-control" style="width:120px; max-width: 200px;" name="fieldname1">
                    <select class="form-control" style="width:80px; max-width: 100px;" name="operator">
                        <option value="$eq">$eq</option>
                        <option value="$ne">$ne</option>
                    </select> <input class="form-control" style="width:120px; max-width: 200px;" type="text"
                                     name="value"><br>

                </div>
                <div class="row">
                    <#if resultList??>
                        <div class="card-header">
                        <#list resultList as r>
                            <div class="card-body">
                                ${r}
                            </div>
                        </#list>
                        </div>
                    <#elseif result??>
                        <div class="card-header">
                            ${result}
                        </div>
                    </#if>
                </div>
                <div class="row">
                    <div class="col-sm-12">
                        <br>
                        <table class="table table-bordered dataTable" id="dataTable" width="100%" cellspacing="0"
                               role="grid" aria-describedby="dataTable_info" style="width: 100%;">
                            <thead>
                            <tr role="row">
                                <th class="sorting sorting_asc" tabindex="0" aria-controls="dataTable" rowspan="1"
                                    colspan="1" aria-sort="ascending"
                                    aria-label="Name: activate to sort column descending" style="width: 242.125px;">Name
                                </th>
                                <th class="sorting" tabindex="0" aria-controls="dataTable" rowspan="1" colspan="1"
                                    aria-label="Position: activate to sort column ascending"
                                    style="width: 387.15625px;">Position
                                </th>
                                <th class="sorting" tabindex="0" aria-controls="dataTable" rowspan="1" colspan="1"
                                    aria-label="Office: activate to sort column ascending" style="width: 176.921875px;">
                                    Office
                                </th>
                                <th class="sorting" tabindex="0" aria-controls="dataTable" rowspan="1" colspan="1"
                                    aria-label="Age: activate to sort column ascending" style="width: 87.484375px;">Age
                                </th>
                                <th class="sorting" tabindex="0" aria-controls="dataTable" rowspan="1" colspan="1"
                                    aria-label="Start date: activate to sort column ascending"
                                    style="width: 167.78125px;">Start date
                                </th>
                                <th class="sorting" tabindex="0" aria-controls="dataTable" rowspan="1" colspan="1"
                                    aria-label="Salary: activate to sort column ascending" style="width: 151.578125px;">
                                    Salary
                                </th>
                            </tr>
                            </thead>
                            <tfoot>
                            <tr>
                                <th rowspan="1" colspan="1">Name</th>
                                <th rowspan="1" colspan="1">Position</th>
                                <th rowspan="1" colspan="1">Office</th>
                                <th rowspan="1" colspan="1">Age</th>
                                <th rowspan="1" colspan="1">Start date</th>
                                <th rowspan="1" colspan="1">Salary</th>
                            </tr>
                            </tfoot>
                            <tbody>
                            <tr class="odd">
                                <td class="sorting_1">Airi Satou</td>
                                <td>Accountant</td>
                                <td>Tokyo</td>
                                <td>33</td>
                                <td>2008/11/28</td>
                                <td>$162,700</td>
                            </tr>
                            <tr class="even">
                                <td class="sorting_1">Angelica Ramos</td>
                                <td>Chief Executive Officer (CEO)</td>
                                <td>London</td>
                                <td>47</td>
                                <td>2009/10/09</td>
                                <td>$1,200,000</td>
                            </tr>
                            <tr class="odd">
                                <td class="sorting_1">Ashton Cox</td>
                                <td>Junior Technical Author</td>
                                <td>San Francisco</td>
                                <td>66</td>
                                <td>2009/01/12</td>
                                <td>$86,000</td>
                            </tr>
                            <tr class="even">
                                <td class="sorting_1">Bradley Greer</td>
                                <td>Software Engineer</td>
                                <td>London</td>
                                <td>41</td>
                                <td>2012/10/13</td>
                                <td>$132,000</td>
                            </tr>
                            <tr class="odd">
                                <td class="sorting_1">Brenden Wagner</td>
                                <td>Software Engineer</td>
                                <td>San Francisco</td>
                                <td>28</td>
                                <td>2011/06/07</td>
                                <td>$206,850</td>
                            </tr>
                            <tr class="even">
                                <td class="sorting_1">Brielle Williamson</td>
                                <td>Integration Specialist</td>
                                <td>New York</td>
                                <td>61</td>
                                <td>2012/12/02</td>
                                <td>$372,000</td>
                            </tr>
                            <tr class="odd">
                                <td class="sorting_1">Bruno Nash</td>
                                <td>Software Engineer</td>
                                <td>London</td>
                                <td>38</td>
                                <td>2011/05/03</td>
                                <td>$163,500</td>
                            </tr>
                            <tr class="even">
                                <td class="sorting_1">Caesar Vance</td>
                                <td>Pre-Sales Support</td>
                                <td>New York</td>
                                <td>21</td>
                                <td>2011/12/12</td>
                                <td>$106,450</td>
                            </tr>
                            <tr class="odd">
                                <td class="sorting_1">Cara Stevens</td>
                                <td>Sales Assistant</td>
                                <td>New York</td>
                                <td>46</td>
                                <td>2011/12/06</td>
                                <td>$145,600</td>
                            </tr>
                            <tr class="even">
                                <td class="sorting_1">Cedric Kelly</td>
                                <td>Senior Javascript Developer</td>
                                <td>Edinburgh</td>
                                <td>22</td>
                                <td>2012/03/29</td>
                                <td>$433,060</td>
                            </tr>
                            </tbody>
                        </table>
                    </div>
                </div>


            </div>
            <!-- /.container-fluid -->

        </div>
        <!-- End of Main Content -->

        <!-- Footer -->
        <#include "includes/footer.ftlh"/>
        <!-- End of Footer -->

    </div>
    <!-- End of Content Wrapper -->

</div>
<!-- End of Page Wrapper -->

<!-- Scroll to Top Button-->
<a class="scroll-to-top rounded" href="#page-top">
    <i class="fas fa-angle-up"></i>
</a>


<!-- Bootstrap core JavaScript-->
<script src="/vendor/jquery/jquery.min.js"></script>
<script src="/vendor/bootstrap/js/bootstrap.bundle.min.js"></script>

<!-- Core plugin JavaScript-->
<script src="/vendor/jquery-easing/jquery.easing.min.js"></script>

<!-- Custom scripts for all pages-->
<script src="/js/sb-admin-2.min.js"></script>
<script src="vendor/datatables/jquery.dataTables.min.js"></script>
<script src="vendor/datatables/dataTables.bootstrap4.min.js"></script>
<script type="text/javascript">
    // Call the dataTables jQuery plugin
    $(document).ready(function () {
        $('#dataTable').DataTable();
    });
</script>
</body>

</html>
